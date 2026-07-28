package net.microfalx.argus.api;

import net.microfalx.lang.FormatterUtils;

import java.time.Duration;

/**
 * An enum representing the unit of measurement for a metric used to calculate scores.
 */
public enum Unit {

    /**
     * A value between 0 and 100.
     */
    PERCENT,

    /**
     * A value in milliseconds.
     */
    MILLISECOND,

    /**
     * A value in nanoseconds.
     */
    NANOSECOND,

    /**
     * A value without any unit of measurement
     */
    COUNTER,

    /**
     * A value representing bytes
     */
    BYTE,

    /**
     * A value representing
     */
    THROUGHPUT;

    /**
     * Formats the value using the current unit.
     *
     * @param value the value
     * @return a formatted string representation of the value
     */
    public String format(float value) {
        return switch (this) {
            case PERCENT -> FormatterUtils.formatPercent(value);
            case BYTE -> FormatterUtils.formatBytes(value);
            case NANOSECOND -> FormatterUtils.formatDuration(Duration.ofNanos((long)value));
            case MILLISECOND -> FormatterUtils.formatDuration(Duration.ofMillis((long)value));
            case THROUGHPUT -> FormatterUtils.formatThroughput(value, "r/s");
            case COUNTER -> FormatterUtils.formatNumber(value, 0);
            default -> throw new IllegalArgumentException("Unsupported unit: " + this);
        };
    }
}
