package net.microfalx.argus.report;

import net.microfalx.argus.api.Health;
import net.microfalx.argus.api.HealthService;
import net.microfalx.argus.api.Resource;
import net.microfalx.lang.*;
import net.microfalx.lang.service.Service;
import net.microfalx.metrics.statistics.Trend;
import net.microfalx.metrics.statistics.TrendStatisticalSummary;
import org.apache.commons.lang3.ArrayUtils;

import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.StringJoiner;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

import static net.microfalx.lang.StringUtils.EMPTY_STRING;

/**
 * Various helper methods for reports.
 */
public class ReportHelper {

    private static final boolean DEMO_TRENDS = Boolean.parseBoolean(System.getProperty("argus.report.trends.demo", "false"));
    private static final ZonedDateTime startupTime = ZonedDateTime.now();
    private static final int SECURE_WORD_COUNT = 5;
    private static final int MAX_TREND_POINTS = 50;
    private static final int MAX_WORSE_ITEMS = 3;
    private static final String EXPANDER_ICON = "fa-solid fa-caret-right";
    static final ZonedDateTime currentTime = ZonedDateTime.now();

    private final Report report;

    public ReportHelper(Report report) {
        this.report = report;
    }

    public ZonedDateTime getStartupTime() {
        return startupTime;
    }

    public ZonedDateTime getCurrentTime() {
        return currentTime;
    }

    public String formatDateTime(Object temporal) {
        return FormatterUtils.formatDateTime(temporal);
    }

    public String formatBytes(Number value) {
        return FormatterUtils.formatBytes(value);
    }

    public String formatPercent(Number value) {
        return FormatterUtils.formatPercent(value);
    }

    public String formatDuration(Duration duration) {
        return FormatterUtils.formatDuration(duration);
    }

    public String formatNumber(Number number) {
        return FormatterUtils.formatNumber(number);
    }

    public String formatScore(float score) {
        if (DEMO_TRENDS) score = getDemoScore();
        if (score == Health.MAX) {
            return formatNumber(10);
        } else {
            return formatNumber(score);
        }
    }

    public String getRibbonCls(Health.Severity severity) {
        if (severity == null) return "";
        if (DEMO_TRENDS) severity = Health.toSeverity(getDemoScore());
        return switch (severity) {
            case NA -> "bg-gray";
            case OK -> "bg-green";
            case LOW -> "bg-yellow";
            case MEDIUM -> "bg-orange";
            case HIGH -> "bg-red bg-opacity-75";
            case CRITICAL -> "bg-red";
        };
    }

    public Collection<Service.Statistics<?>> getServices(Collection<Service.Statistics<?>> statistics) {
        return statistics;
    }

    public String getTooltip(Service.Statistics<?> statistics) {
        return String.format("Class Name: %s" +
                        "\nDescription: %s",
                statistics.getClassName(),
                statistics.getDescription());
    }

    public String getBadgeFromScoreCls(float score) {
        if (DEMO_TRENDS) score = getDemoScore();
        return getBadgeCls(Health.toSeverity(score));
    }

    public String getBadgeFromScoreCls(Float score) {
        if (score == null) return "";
        if (DEMO_TRENDS) score = getDemoScore();
        return getBadgeFromScoreCls(score.floatValue());
    }

    public String getBadgeCls(Health.Severity severity) {
        if (severity == null) return "";
        String result = switch (severity) {
            case NA -> "bg-gray text-gray-fg";
            case OK -> "bg-green text-green-fg";
            case LOW -> "bg-yellow text-yellow-fg";
            case MEDIUM -> "bg-orange text-orange-fg";
            case HIGH -> "bg-red text-red-fg bg-opacity-75";
            case CRITICAL -> "bg-red text-red-fg";
            default -> "";
        };
        return "badge ms-2 " + result;
    }

    public String getGroupStyle(Health.Group group) {
        return "padding-left: " + (0.75 + (group.getDepth() - 1) / 2f) + "rem";
    }

    public boolean hasGroupIcon(Health.Group group, boolean all) {
        return group.getDepth() > 1 || (all && !group.getItems().isEmpty());
    }

    public String getGroupIconStyle(Health.Group group) {
        String style = "";
        if (!group.getItems().isEmpty()) {
            style = "cursor: pointer";
        }
        return style;
    }

    public String getGroupIconClass(Health.Group group) {
        String result = hasGroupIcon(group, true) ? EXPANDER_ICON : EMPTY_STRING;
        if (!group.getItems().isEmpty()) {
            result += " text-primary";
        }
        return result + " me-2";
    }

    public String getTrend(Health.Item item) {
        return getTrend(item, MAX_TREND_POINTS);
    }

    public String getTrend(Health.Item item, int maxPoints) {
        if (item == null) return EMPTY_STRING;
        return getTrend(item.getTrend(), maxPoints);
    }

    public String getTrend(Health.Group group) {
        if (group == null) return EMPTY_STRING;
        return getTrend(group.getTrend());
    }

    public String getTrend(Resource resource) {
        if (resource == null) return EMPTY_STRING;
        return getTrend(resource.getHealth().getTrend());
    }

    public <S extends Service> String getTrendMemory(Service.Statistics<S> serviceStatistics, int maxPoints) {
        if (serviceStatistics == null) return EMPTY_STRING;
        TrendStatisticalSummary trend = HealthService.getInstance().getTrend(serviceStatistics.getService(), Service.Metric.MEMORY_USAGE);
        return getTrend(trend, maxPoints, TrendType.MEMORY);
    }

    public String getTrend(TrendStatisticalSummary summary) {
        return getTrend(summary, MAX_TREND_POINTS);
    }

    public String getTrend(TrendStatisticalSummary summary, int maxPoints) {
        return getTrend(summary, maxPoints, TrendType.SCORE);
    }

    public String getTrend(TrendStatisticalSummary summary, int maxPoints, TrendType type) {
        if (summary == null) return EMPTY_STRING;
        if (DEMO_TRENDS) {
            return switch (type) {
                case SCORE -> getScoreDemoTrendValues();
                case MEMORY -> getMemoryDemoTrendValues();
            };
        }
        return Arrays.stream(summary.getValues(maxPoints))
                .mapToObj(this::formatTrendValue)
                .collect(Collectors.joining(","));
    }

    public <S extends Service> String getTrendMemoryGlyph(Service.Statistics<S> serviceStatistics) {
        if (serviceStatistics == null) return EMPTY_STRING;
        return getTrendGlyph(HealthService.getInstance().getTrend(serviceStatistics.getService(), Service.Metric.MEMORY_USAGE));
    }

    public String getTrendGlyph(Health.Item item) {
        if (item == null) return EMPTY_STRING;
        return getTrendGlyph(item.getTrend());
    }

    public String getTrendGlyph(Health.Group group) {
        if (group == null) return EMPTY_STRING;
        return getTrendGlyph(group.getTrend());
    }

    public String getTrendGlyph(Resource resource) {
        if (resource == null) return EMPTY_STRING;
        return getTrendGlyph(resource.getHealth().getTrend());
    }

    public String getTrendGlyph(TrendStatisticalSummary summary) {
        if (summary == null) return EMPTY_STRING;
        if (DEMO_TRENDS) return getDemoTrend().toHtml();
        return summary.getTrend().toHtml();
    }

    public Collection<Health.Item> getReportItems(Health.Group group) {
        return getReportItems(group.getItems());
    }

    public Collection<Health.Item> getReportItems(Collection<Health.Item> items) {
        return items.stream().filter(Health.Item::shouldReport).toList();
    }

    public Collection<Health.Item> getWorseItems(Health health) {
        return health.getGroups().stream().flatMap(group -> group.getItems(true).stream())
                .filter(Health.Item::hasIssues)
                .sorted(Comparator.comparing(Health.Item::getScore))
                .limit(MAX_WORSE_ITEMS)
                .toList();
    }

    public boolean hasWorseItems(Health health) {
        return !getWorseItems(health).isEmpty();
    }

    public String toString(Object value) {
        if (value instanceof Collection<?>) {
            StringBuilder builder = new StringBuilder();
            for (Object o : (Collection<?>) value) {
                StringUtils.append(builder, o, ", ");
            }
            return builder.toString();
        } else {
            return ObjectUtils.toString(value);
        }
    }

    public String toDisplay(Object value) {
        String text = toString(value);
        return StringUtils.isEmpty(text) ? "-" : text;
    }

    public String toDisplay(Object value, int maxLength) {
        return TextUtils.abbreviateMiddle(toDisplay(value), maxLength);
    }

    public String toSummary(Object value) {
        return toSummary(value, Integer.MAX_VALUE);
    }

    public String toSummary(Object value, int maxLength) {
        if (report == null || report.isSecure()) {
            String text = toDisplay(value);
            String[] parts = StringUtils.split(text, " ");
            String[] remaining = ArrayUtils.subarray(parts, 0, SECURE_WORD_COUNT);
            String scrambled = String.join(" ", remaining).trim();
            if (parts.length > remaining.length) {
                scrambled += "...";
            }
            return scrambled;
        } else {
            return toDisplay(value, maxLength);
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public String toLabel(Object value) {
        if (value instanceof Enum) {
            return EnumUtils.toLabel((Enum) value);
        } else if (value instanceof Class<?> clazz) {
            return ClassUtils.getCompactName(clazz);
        } else {
            return toDisplay(value);
        }
    }

    public String toHtmlId(Object value) {
        if (value == null) return null;
        return "#" + ObjectUtils.toString(value);
    }

    private String formatTrendValue(Number value) {
        return formatTrendValue(value, 1);
    }

    private String formatTrendValue(Number value, int decimalsPlaces) {
        boolean isFloating = value instanceof Float || value instanceof Double;
        String text;
        if (isFloating) {
            double valueAsDouble = value.doubleValue();
            text = String.format("%." + decimalsPlaces + "f", valueAsDouble);
        } else {
            long valueAsLong = value.longValue();
            text = String.format("%d", valueAsLong);
        }
        return text;
    }

    private Trend getDemoTrend() {
        return Trend.values()[(int) (Math.random() * Trend.values().length)];
    }

    private static float getDemoScore() {
        return 1 + ThreadLocalRandom.current().nextFloat(9);
    }

    private String getScoreDemoTrendValues() {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        StringJoiner joiner = new StringJoiner(",");
        for (int i = 0; i < MAX_TREND_POINTS; i++) {
            float value = 1 + random.nextFloat(9);
            joiner.add(FormatterUtils.formatNumber(value));
        }
        return joiner.toString();
    }

    private String getMemoryDemoTrendValues() {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        StringJoiner joiner = new StringJoiner(",");
        for (int i = 0; i < MAX_TREND_POINTS; i++) {
            float value = 500 * FormatterUtils.M + 20000 * FormatterUtils.M * random.nextFloat();
            joiner.add(FormatterUtils.formatNumber(value));
        }
        return joiner.toString();
    }

    enum TrendType {
        SCORE, MEMORY
    }

    static final long[] DURATION_BUCKETS = new long[]{
            1, 5, 10, 20, 50, 100, 200, 500, 1_000, 2_000, 5_000, 10_000, 20_000, 30_000, 60_000
    };
    private static final int DURATION_BUCKETS_LENGTH = DURATION_BUCKETS.length;

    static final String[] DURATION_BUCKET_NAMES = new String[]{
            "<1ms", "5ms", "10ms", "20ms", "50ms", "100ms", "200ms", "500ms", "1s", "2s", "5s", "10s", "20s", "30s", ">60s"
    };

}
