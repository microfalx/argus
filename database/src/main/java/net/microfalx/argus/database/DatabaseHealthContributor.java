package net.microfalx.argus.database;

import net.microfalx.argus.api.Health;
import net.microfalx.argus.api.Resource;
import net.microfalx.argus.api.Thresholds;
import net.microfalx.argus.api.Unit;
import net.microfalx.argus.core.contributor.AbstractHealthContributor;
import net.microfalx.database.Database;
import net.microfalx.database.DatabaseService;
import net.microfalx.lang.NumberUtils;
import net.microfalx.lang.annotation.Order;
import net.microfalx.lang.annotation.Provider;
import net.microfalx.lang.annotation.Tag;
import net.microfalx.metrics.Batch;
import net.microfalx.metrics.Metric;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.util.*;

import static net.microfalx.lang.ExceptionUtils.getRootCauseMessage;
import static net.microfalx.lang.StringUtils.isNotEmpty;

/**
 * Assesses the health of the (default) database, from the server side.
 * <p>
 * Only MySQL is supported for now; the health is calculated from the global status and variables, InnoDB metrics,
 * running transactions, statement latency histogram (performance schema) and replication status.
 * <p>
 * Each source, except the global status, is optional: if the user has no privileges to query it, it is skipped.
 */
@SuppressWarnings("FieldMayBeFinal")
@Provider
@Order(200)
@Tag("database")
public class DatabaseHealthContributor extends AbstractHealthContributor {

    private final static String CONNECTIONS_GROUP = "Connections";
    private final static String QUERIES_GROUP = "Queries";
    private final static String INNODB_GROUP = "InnoDB";
    private final static String TRANSACTIONS_GROUP = "Transactions";
    private final static String REPLICATION_GROUP = "Replication";

    private final static int LONG_TRANSACTION_SECONDS = 60;

    private final static String STATUS_QUERY = "SHOW GLOBAL STATUS WHERE Variable_name IN ('Threads_connected', 'Threads_running', "
            + "'Aborted_connects', 'Connection_errors_max_connections', 'Slow_queries', 'Created_tmp_tables', 'Created_tmp_disk_tables', "
            + "'Innodb_buffer_pool_reads', 'Innodb_buffer_pool_read_requests', 'Innodb_row_lock_current_waits', "
            + "'Innodb_row_lock_waits', 'Innodb_row_lock_time')";
    private final static String VARIABLES_QUERY = "SHOW GLOBAL VARIABLES WHERE Variable_name IN ('max_connections')";
    private final static String INNODB_METRICS_QUERY = "SELECT NAME, COUNT FROM information_schema.INNODB_METRICS "
            + "WHERE NAME IN ('trx_rseg_history_len', 'lock_deadlocks') AND STATUS = 'enabled'";
    private final static String TRANSACTIONS_QUERY = "SELECT 'long_transactions', COUNT(*) FROM information_schema.INNODB_TRX "
            + "WHERE trx_started < NOW() - INTERVAL " + LONG_TRANSACTION_SECONDS + " SECOND "
            + "UNION ALL SELECT 'oldest_transaction', COALESCE(MAX(TIMESTAMPDIFF(SECOND, trx_started, NOW())), 0) "
            + "FROM information_schema.INNODB_TRX";
    private final static String HISTOGRAM_QUERY = "SELECT BUCKET_TIMER_HIGH, COUNT_BUCKET "
            + "FROM performance_schema.events_statements_histogram_global ORDER BY BUCKET_NUMBER";
    private final static String REPLICATION_QUERY = "SHOW REPLICA STATUS";

    private Map<String, Long> lastStatus;
    private long lastStatusTime;
    private long[] lastHistogramCounts;

    @Override
    public String getName() {
        return "Database";
    }

    @Override
    public boolean supports(Resource.Type type) {
        return type == Resource.Type.DATABASE;
    }

    @Override
    public void update(Batch batch) {
        Health health = getHealth(Resource.Type.DATABASE);
        batch.add(SCORE_METRIC, health.getScore());
    }

    @Override
    public void update(Health health) {
        Optional<Database> database = getDefaultDatabase();
        if (database.isEmpty() || database.get().getType() != Database.Type.MYSQL) return;
        long start = System.nanoTime();
        try (Connection connection = database.get().getDataSource().getConnection()) {
            Map<String, Long> status = query(connection, STATUS_QUERY);
            health.getGroup(QUERIES_GROUP).update(asDurationItem(RESPONSE_TIME, Duration.ofNanos(System.nanoTime() - start)));
            Map<String, Long> variables = query(connection, VARIABLES_QUERY);
            status.putAll(queryOptional(connection, INNODB_METRICS_QUERY));
            Map<String, Long> transactions = queryOptional(connection, TRANSACTIONS_QUERY);
            long[][] histogram = queryHistogram(connection);
            updateTransactions(health.getGroup(TRANSACTIONS_GROUP), transactions);
            synchronized (lock) {
                updateConnections(health.getGroup(CONNECTIONS_GROUP), status, variables);
                updateQueries(health.getGroup(QUERIES_GROUP), status, histogram);
                updateInnoDb(health.getGroup(INNODB_GROUP), status);
                lastStatus = status;
                lastStatusTime = System.currentTimeMillis();
                if (histogram != null) lastHistogramCounts = histogram[1];
            }
            updateReplication(health.getGroup(REPLICATION_GROUP), connection);
        } catch (Exception e) {
            health.getGroup(CONNECTIONS_GROUP).update(Health.Item.create(AVAILABILITY_NAME, Health.MIN)
                    .withDescription(getRootCauseMessage(e)));
        }
    }

    @Override
    public Collection<Thresholds> getThresholds() {
        return List.of(CONNECTIONS, RUNNING_THREADS, ABORTED_CONNECTIONS, REJECTED_CONNECTIONS,
                RESPONSE_TIME, LATENCY_P95, LATENCY_P99, SLOW_QUERIES, TEMPORARY_DISK_TABLES,
                BUFFER_POOL_MISSES, ROW_LOCK_WAITS, ROW_LOCK_TIME, DEADLOCKS,
                LONG_TRANSACTIONS, OLDEST_TRANSACTION, HISTORY_LENGTH, REPLICATION_LAG);
    }

    private void updateConnections(Health.Group group, Map<String, Long> status, Map<String, Long> variables) {
        group.update(Health.Item.create(CONNECTIONS, get(status, "Threads_connected"),
                get(variables, "max_connections"), Unit.COUNTER));
        group.update(asCounterItem(RUNNING_THREADS, (int) get(status, "Threads_running")));
        if (lastStatus == null) return;
        group.update(asCounterItem(ABORTED_CONNECTIONS, (int) getRatePerMinute(status, "Aborted_connects")));
        group.update(asCounterItem(REJECTED_CONNECTIONS, (int) getRatePerMinute(status, "Connection_errors_max_connections")));
    }

    private void updateQueries(Health.Group group, Map<String, Long> status, long[][] histogram) {
        if (lastStatus == null) return;
        group.update(asCounterItem(SLOW_QUERIES, (int) getRatePerMinute(status, "Slow_queries")));
        group.update(asPercentageItem(TEMPORARY_DISK_TABLES, NumberUtils.percent(getDelta(status, "Created_tmp_disk_tables"),
                getDelta(status, "Created_tmp_tables"))));
        updateLatency(group, histogram);
    }

    private void updateLatency(Health.Group group, long[][] histogram) {
        if (histogram == null || lastHistogramCounts == null || lastHistogramCounts.length != histogram[1].length)
            return;
        long[] timers = histogram[0];
        long[] counts = new long[timers.length];
        long total = 0;
        for (int i = 0; i < counts.length; i++) {
            // counters are reset when the server restarts or performance schema is truncated
            counts[i] = Math.max(0, histogram[1][i] - lastHistogramCounts[i]);
            total += counts[i];
        }
        if (total == 0) return;
        group.update(asDurationItem(LATENCY_P95, getPercentile(timers, counts, total, 0.95)));
        group.update(asDurationItem(LATENCY_P99, getPercentile(timers, counts, total, 0.99)));
    }

    private Duration getPercentile(long[] timers, long[] counts, long total, double percentile) {
        long rank = (long) Math.ceil(total * percentile);
        long cumulative = 0;
        for (int i = 0; i < counts.length; i++) {
            cumulative += counts[i];
            // timers are in picoseconds
            if (cumulative >= rank) return Duration.ofNanos(timers[i] / 1000);
        }
        return Duration.ofNanos(timers[timers.length - 1] / 1000);
    }

    private void updateInnoDb(Health.Group group, Map<String, Long> status) {
        group.update(asCounterItem(ROW_LOCK_WAITS, (int) get(status, "Innodb_row_lock_current_waits")));
        if (status.containsKey("trx_rseg_history_len")) {
            group.update(asCounterItem(HISTORY_LENGTH, (int) Math.min(Integer.MAX_VALUE, get(status, "trx_rseg_history_len"))));
        }
        if (lastStatus == null) return;
        group.update(asPercentageItem(BUFFER_POOL_MISSES, NumberUtils.percent(getDelta(status, "Innodb_buffer_pool_reads"),
                getDelta(status, "Innodb_buffer_pool_read_requests"))));
        long lockWaits = getDelta(status, "Innodb_row_lock_waits");
        if (lockWaits > 0) {
            group.update(asDurationItem(ROW_LOCK_TIME, Duration.ofMillis(getDelta(status, "Innodb_row_lock_time") / lockWaits)));
        }
        if (status.containsKey("lock_deadlocks") && lastStatus.containsKey("lock_deadlocks")) {
            group.update(asCounterItem(DEADLOCKS, (int) Math.ceil(getRatePerMinute(status, "lock_deadlocks"))));
        }
    }

    private void updateTransactions(Health.Group group, Map<String, Long> transactions) {
        if (transactions.isEmpty()) return;
        group.update(asCounterItem(LONG_TRANSACTIONS, (int) get(transactions, "long_transactions")));
        group.update(asDurationItem(OLDEST_TRANSACTION, Duration.ofSeconds(get(transactions, "oldest_transaction"))));
    }

    private void updateReplication(Health.Group group, Connection connection) {
        try (Statement statement = connection.createStatement(); ResultSet resultSet = statement.executeQuery(REPLICATION_QUERY)) {
            while (resultSet.next()) {
                String channel = resultSet.getString("Channel_Name");
                String suffix = isNotEmpty(channel) ? " (" + channel + ")" : "";
                updateReplicationThread(group, "IO Thread" + suffix, resultSet.getString("Replica_IO_Running"),
                        resultSet.getString("Last_IO_Error"));
                updateReplicationThread(group, "SQL Thread" + suffix, resultSet.getString("Replica_SQL_Running"),
                        resultSet.getString("Last_SQL_Error"));
                long lag = resultSet.getLong("Seconds_Behind_Source");
                if (!resultSet.wasNull()) {
                    group.update(asDurationItem(REPLICATION_LAG, Duration.ofSeconds(lag)).withName(REPLICATION_LAG.getName() + suffix));
                }
            }
        } catch (SQLException e) {
            // not supported (older versions) or not enough privileges
        }
    }

    private void updateReplicationThread(Health.Group group, String name, String running, String error) {
        if ("Yes".equalsIgnoreCase(running)) {
            group.update(Health.Item.create(name, Health.MAX).withDescription("Running"));
        } else {
            String description = isNotEmpty(error) ? error : Objects.toString(running, "Not running");
            group.update(Health.Item.create(name, Health.MIN).withDescription(description));
        }
    }

    private long get(Map<String, Long> values, String name) {
        return values.getOrDefault(name, 0L);
    }

    private long getDelta(Map<String, Long> status, String name) {
        // counters are reset when the server restarts
        return Math.max(0, get(status, name) - get(lastStatus, name));
    }

    private float getRatePerMinute(Map<String, Long> status, String name) {
        long interval = Math.max(1, System.currentTimeMillis() - lastStatusTime);
        return getDelta(status, name) * 60_000f / interval;
    }

    private Map<String, Long> query(Connection connection, String sql) throws SQLException {
        Map<String, Long> values = new HashMap<>();
        try (Statement statement = connection.createStatement(); ResultSet resultSet = statement.executeQuery(sql)) {
            while (resultSet.next()) {
                try {
                    values.put(resultSet.getString(1), Long.parseLong(resultSet.getString(2)));
                } catch (NumberFormatException e) {
                    // ignore non-numeric values
                }
            }
        }
        return values;
    }

    private Map<String, Long> queryOptional(Connection connection, String sql) {
        try {
            return query(connection, sql);
        } catch (SQLException e) {
            // not enough privileges
            return new HashMap<>();
        }
    }

    private long[][] queryHistogram(Connection connection) {
        List<long[]> buckets = new ArrayList<>();
        try (Statement statement = connection.createStatement(); ResultSet resultSet = statement.executeQuery(HISTOGRAM_QUERY)) {
            while (resultSet.next()) {
                buckets.add(new long[]{resultSet.getLong(1), resultSet.getLong(2)});
            }
        } catch (SQLException e) {
            // performance schema disabled or not enough privileges
            return null;
        }
        if (buckets.isEmpty()) return null;
        long[][] histogram = new long[2][buckets.size()];
        for (int i = 0; i < buckets.size(); i++) {
            histogram[0][i] = buckets.get(i)[0];
            histogram[1][i] = buckets.get(i)[1];
        }
        return histogram;
    }

    private Optional<Database> getDefaultDatabase() {
        try {
            return DatabaseService.getInstance().getDefaultDatabase();
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    private final static String AVAILABILITY_NAME = "Availability";

    private static volatile Thresholds CONNECTIONS = Thresholds.create("Connections", 75f, 90f, Unit.PERCENT).withId("database.connections").withGroup(CONNECTIONS_GROUP);
    private static volatile Thresholds RUNNING_THREADS = Thresholds.create("Running Threads", 20, 50, Unit.COUNTER).withId("database.threads.running").withGroup(CONNECTIONS_GROUP);
    private static volatile Thresholds ABORTED_CONNECTIONS = Thresholds.create("Aborted Connections", 5, 20, Unit.COUNTER).withId("database.connections.aborted").withGroup(CONNECTIONS_GROUP);
    private static volatile Thresholds REJECTED_CONNECTIONS = Thresholds.create("Rejected Connections", 1, 5, Unit.COUNTER).withId("database.connections.rejected").withGroup(CONNECTIONS_GROUP);

    private static volatile Thresholds RESPONSE_TIME = Thresholds.create("Response Time", 50, 200, Unit.MILLISECOND).withId("database.response.time").withGroup(QUERIES_GROUP);
    private static volatile Thresholds LATENCY_P95 = Thresholds.create("Latency P95", 100, 500, Unit.MILLISECOND).withId("database.latency.p95").withGroup(QUERIES_GROUP);
    private static volatile Thresholds LATENCY_P99 = Thresholds.create("Latency P99", 500, 2000, Unit.MILLISECOND).withId("database.latency.p99").withGroup(QUERIES_GROUP);
    private static volatile Thresholds SLOW_QUERIES = Thresholds.create("Slow Queries", 5, 20, Unit.COUNTER).withId("database.queries.slow").withGroup(QUERIES_GROUP);
    private static volatile Thresholds TEMPORARY_DISK_TABLES = Thresholds.create("Temporary Disk Tables", 25f, 50f, Unit.PERCENT).withId("database.tables.temporary.disk").withGroup(QUERIES_GROUP);

    private static volatile Thresholds BUFFER_POOL_MISSES = Thresholds.create("Buffer Pool Misses", 1f, 5f, Unit.PERCENT).withId("database.innodb.buffer.pool.misses").withGroup(INNODB_GROUP);
    private static volatile Thresholds ROW_LOCK_WAITS = Thresholds.create("Row Lock Waits", 5, 20, Unit.COUNTER).withId("database.innodb.row.lock.waits").withGroup(INNODB_GROUP);
    private static volatile Thresholds ROW_LOCK_TIME = Thresholds.create("Row Lock Time", 50, 500, Unit.MILLISECOND).withId("database.innodb.row.lock.time").withGroup(INNODB_GROUP);
    private static volatile Thresholds DEADLOCKS = Thresholds.create("Deadlocks", 1, 5, Unit.COUNTER).withId("database.innodb.deadlocks").withGroup(INNODB_GROUP);
    private static volatile Thresholds HISTORY_LENGTH = Thresholds.create("History Length", 10_000, 100_000, Unit.COUNTER).withId("database.innodb.history.length").withGroup(INNODB_GROUP);

    private static volatile Thresholds LONG_TRANSACTIONS = Thresholds.create("Long Transactions", 1, 5, Unit.COUNTER).withId("database.transactions.long").withGroup(TRANSACTIONS_GROUP);
    private static volatile Thresholds OLDEST_TRANSACTION = Thresholds.create("Oldest Transaction", 300_000, 1_800_000, Unit.MILLISECOND).withId("database.transactions.oldest").withGroup(TRANSACTIONS_GROUP);

    private static volatile Thresholds REPLICATION_LAG = Thresholds.create("Lag", 30_000, 300_000, Unit.MILLISECOND).withId("database.replication.lag").withGroup(REPLICATION_GROUP);

    public static final Metric SCORE_METRIC = Metric.get("database.score").withGroup("Health").withDisplayName("Database Score");
}
