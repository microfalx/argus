package net.microfalx.argus.database;

import com.zaxxer.hikari.HikariDataSource;
import com.zaxxer.hikari.HikariPoolMXBean;
import com.zaxxer.hikari.metrics.IMetricsTracker;
import com.zaxxer.hikari.metrics.MetricsTrackerFactory;
import com.zaxxer.hikari.metrics.PoolStats;
import net.microfalx.argus.api.*;
import net.microfalx.argus.core.contributor.AbstractHealthContributor;
import net.microfalx.database.DataSource;
import net.microfalx.database.DatabaseService;
import net.microfalx.lang.annotation.Order;
import net.microfalx.lang.annotation.Provider;
import net.microfalx.lang.annotation.Tag;
import net.microfalx.metrics.statistics.CounterStatisticalSummary;

import java.sql.Connection;
import java.time.Duration;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAccumulator;
import java.util.concurrent.atomic.LongAdder;

import static net.microfalx.lang.ExceptionUtils.getRootCauseMessage;

/**
 * Assesses the health of the (default) data source used by the service, from the client (connection pool) side.
 * <p>
 * The contributor installs its own Hikari metrics tracker (when the pool has none) to collect acquire times and
 * timeouts, and listens to the logger events to detect connection leaks.
 */
@SuppressWarnings("FieldMayBeFinal")
@Provider
@Order(200)
@Tag("database")
public class DataSourceHealthContributor extends AbstractHealthContributor implements LoggerListener {

    private final static String GROUP = "Data Source";
    private final static String LEAK_LOGGER = "com.zaxxer.hikari.pool.ProxyLeakTask";

    // the instance registered as a logger listener is not the instance registered as a health contributor
    private static final Object LEAKS_LOCK = new Object();
    private static final CounterStatisticalSummary LEAKS = new CounterStatisticalSummary();

    private final Map<String, PoolTracker> trackers = new ConcurrentHashMap<>();

    @Override
    public String getName() {
        return GROUP;
    }

    @Override
    public boolean supports(Resource.Type type) {
        return type == Resource.Type.SERVICE;
    }

    @Override
    public void onEvent(LoggerEvent event) {
        if (event.getLevel() != LoggerEvent.Level.WARN || !LEAK_LOGGER.equals(event.getName())) return;
        synchronized (LEAKS_LOCK) {
            LEAKS.increment();
        }
    }

    @Override
    public void update(Health health) {
        Optional<DataSource> dataSource = getDefaultDataSource();
        if (dataSource.isEmpty()) return;
        if (!(dataSource.get().unwrap() instanceof HikariDataSource hikariDataSource)) return;
        Health.Group group = health.getGroup(GROUP).getGroup(dataSource.get().getName());
        HikariPoolMXBean pool = hikariDataSource.getHikariPoolMXBean();
        if (pool == null) {
            group.update(Health.Item.create(CONNECTION.getName(), Health.MIN).withDescription("Pool not started"));
            return;
        }
        int maximumPoolSize = hikariDataSource.getMaximumPoolSize();
        int activeConnections = pool.getActiveConnections();
        group.update(Health.Item.create(POOL_USAGE, activeConnections, maximumPoolSize, Unit.COUNTER));
        group.update(asCounterItem(PENDING_THREADS, pool.getThreadsAwaitingConnection()));
        updateIdle(group, hikariDataSource, pool);
        updateTracker(group, getTracker(dataSource.get(), hikariDataSource));
        updateLeaks(group, hikariDataSource);
        // do not compete with the application for a connection when the pool is exhausted
        if (activeConnections < maximumPoolSize) updateConnection(group, dataSource.get());
    }

    @Override
    public Collection<Thresholds> getThresholds() {
        return List.of(POOL_USAGE, PENDING_THREADS, IDLE_SHORTFALL, ACQUIRE_TIME, TIMEOUTS, LEAKS_THRESHOLDS, CONNECTION);
    }

    private void updateIdle(Health.Group group, HikariDataSource dataSource, HikariPoolMXBean pool) {
        int minimumIdle = dataSource.getMinimumIdle();
        // when the pool reached its maximum size, missing idle connections means the pool is busy, not broken
        if (minimumIdle <= 0 || pool.getTotalConnections() >= dataSource.getMaximumPoolSize()) return;
        int missing = Math.max(0, minimumIdle - pool.getIdleConnections());
        group.update(Health.Item.create(IDLE_SHORTFALL, missing, minimumIdle, Unit.COUNTER));
    }

    private void updateTracker(Health.Group group, PoolTracker tracker) {
        if (tracker == null) return;
        group.update(asCounterItem(TIMEOUTS, tracker.getTimeouts()));
        long count = tracker.acquireCount.sumThenReset();
        long total = tracker.acquireTotal.sumThenReset();
        long maximum = tracker.acquireMaximum.getThenReset();
        if (count == 0) return;
        Duration average = Duration.ofNanos(total / count);
        group.update(asDurationItem(ACQUIRE_TIME, average).withDescription(Unit.MILLISECOND.format(average.toMillis())
                + " average, " + Unit.MILLISECOND.format(Duration.ofNanos(maximum).toMillis()) + " maximum"));
    }

    private void updateLeaks(Health.Group group, HikariDataSource dataSource) {
        if (dataSource.getLeakDetectionThreshold() <= 0) return;
        float leaks;
        synchronized (LEAKS_LOCK) {
            leaks = LEAKS.getCount();
        }
        group.update(asCounterItem(LEAKS_THRESHOLDS, (int) leaks));
    }

    private void updateConnection(Health.Group group, DataSource dataSource) {
        long start = System.nanoTime();
        try (Connection connection = dataSource.getConnection()) {
            if (connection.isValid((int) dataSource.getTimeout().toSeconds())) {
                group.update(asDurationItem(CONNECTION, Duration.ofNanos(System.nanoTime() - start)));
            } else {
                group.update(Health.Item.create(CONNECTION.getName(), Health.MIN).withDescription("Connection is not valid"));
            }
        } catch (Exception e) {
            group.update(Health.Item.create(CONNECTION.getName(), Health.MIN).withDescription(getRootCauseMessage(e)));
        }
    }

    private PoolTracker getTracker(DataSource dataSource, HikariDataSource hikariDataSource) {
        return trackers.computeIfAbsent(dataSource.getId(), id -> installTracker(hikariDataSource));
    }

    private PoolTracker installTracker(HikariDataSource dataSource) {
        // Hikari accepts only one tracker, do not replace one configured by the application
        if (dataSource.getMetricsTrackerFactory() != null || dataSource.getMetricRegistry() != null) return null;
        PoolTracker tracker = new PoolTracker();
        try {
            dataSource.setMetricsTrackerFactory(tracker);
            return tracker;
        } catch (IllegalStateException e) {
            return null;
        }
    }

    private Optional<DataSource> getDefaultDataSource() {
        try {
            return DatabaseService.getInstance().getDefaultDataSource();
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    /**
     * Collects the connection pool events between two health updates (acquire times) or over the last hour (timeouts).
     */
    private static final class PoolTracker implements MetricsTrackerFactory, IMetricsTracker {

        private final LongAdder acquireCount = new LongAdder();
        private final LongAdder acquireTotal = new LongAdder();
        private final LongAccumulator acquireMaximum = new LongAccumulator(Math::max, 0);
        private final CounterStatisticalSummary timeouts = new CounterStatisticalSummary();

        @Override
        public IMetricsTracker create(String poolName, PoolStats poolStats) {
            return this;
        }

        @Override
        public void recordConnectionAcquiredNanos(long elapsedAcquiredNanos) {
            acquireCount.increment();
            acquireTotal.add(elapsedAcquiredNanos);
            acquireMaximum.accumulate(elapsedAcquiredNanos);
        }

        @Override
        public void recordConnectionTimeout() {
            synchronized (timeouts) {
                timeouts.increment();
            }
        }

        private int getTimeouts() {
            synchronized (timeouts) {
                return (int) timeouts.getCount();
            }
        }
    }

    private static volatile Thresholds POOL_USAGE = Thresholds.create("Pool Usage", 75f, 90f, Unit.PERCENT).withId("datasource.pool.usage").withGroup(GROUP);
    private static volatile Thresholds PENDING_THREADS = Thresholds.create("Pending Threads", 2, 10, Unit.COUNTER).withId("datasource.pool.pending").withGroup(GROUP);
    private static volatile Thresholds IDLE_SHORTFALL = Thresholds.create("Idle Shortfall", 50f, 90f, Unit.PERCENT).withId("datasource.pool.idle.shortfall").withGroup(GROUP);
    private static volatile Thresholds ACQUIRE_TIME = Thresholds.create("Acquire Time", 50, 250, Unit.MILLISECOND).withId("datasource.acquire.time").withGroup(GROUP);
    private static volatile Thresholds TIMEOUTS = Thresholds.create("Timeouts", 1, 10, Unit.COUNTER).withId("datasource.timeouts").withGroup(GROUP);
    private static volatile Thresholds LEAKS_THRESHOLDS = Thresholds.create("Leaks", 1, 5, Unit.COUNTER).withId("datasource.leaks").withGroup(GROUP);
    private static volatile Thresholds CONNECTION = Thresholds.create("Connection", 100, 500, Unit.MILLISECOND).withId("datasource.connection").withGroup(GROUP);
}
