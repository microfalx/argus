package net.microfalx.argus.api;

import net.microfalx.lang.FormatterUtils;

import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static net.microfalx.lang.ArgumentUtils.requireNotEmpty;
import static net.microfalx.lang.FormatterUtils.*;

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
     * A value representing throughput
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

    /**
     * Parses a value expressed in the current unit.
     * <p>
     * A plain number is expected to be in the base unit (percent, milliseconds, nanoseconds, bytes, etc.). The
     * parser also accepts the suffixes produced by {@link #format(float)}, like {@code 85%}, {@code 50ms},
     * {@code 1s 500ms}, {@code 5m}, {@code 10KB}, {@code 2k} or {@code 10r/s}. Decimals use '.' as separator.
     *
     * @param value the value to parse
     * @return the value in the base unit
     * @throws IllegalArgumentException if the value cannot be parsed
     */
    public float parse(String value) {
        requireNotEmpty(value);
        String text = value.trim().replace("~", "");
        try {
            return switch (this) {
                case PERCENT -> parseNumber(removeEndIgnoreCase(text, "%"));
                case MILLISECOND -> parseDuration(text, TimeUnit.MILLISECONDS.toNanos(1));
                case NANOSECOND -> parseDuration(text, 1);
                case COUNTER -> parseScaled(text, 'k', 'm', 'b');
                case BYTE -> parseScaled(removeEndIgnoreCase(text, "b"), 'k', 'm', 'g');
                case THROUGHPUT -> parseScaled(removeEndIgnoreCase(text.replace("/s", ""), "r"), 'k', 'm', 'b');
            };
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid value '" + value + "' for unit " + this, e);
        }
    }

    private static float parseNumber(String text) {
        return Float.parseFloat(text.trim());
    }

    private static float parseScaled(String text, char thousands, char millions, char billions) {
        text = text.trim();
        if (text.isEmpty()) throw new NumberFormatException("Empty value");
        char suffix = Character.toLowerCase(text.charAt(text.length() - 1));
        long multiplier = 1;
        if (suffix == thousands) {
            multiplier = K;
        } else if (suffix == millions) {
            multiplier = M;
        } else if (suffix == billions) {
            multiplier = G;
        }
        if (multiplier > 1) text = text.substring(0, text.length() - 1);
        return parseNumber(text) * multiplier;
    }

    private static float parseDuration(String text, long nanosPerUnit) {
        String compact = text.replaceAll("\\s+", "");
        Matcher matcher = DURATION_PATTERN.matcher(compact);
        double nanos = 0;
        int index = 0;
        while (index < compact.length()) {
            matcher.region(index, compact.length());
            if (!matcher.lookingAt()) throw new NumberFormatException("Invalid duration '" + text + "'");
            double amount = Double.parseDouble(matcher.group(1));
            String suffix = matcher.group(2);
            nanos += amount * (suffix == null ? nanosPerUnit : getNanos(suffix));
            index = matcher.end();
        }
        if (index == 0) throw new NumberFormatException("Empty duration");
        return (float) (nanos / nanosPerUnit);
    }

    private static long getNanos(String suffix) {
        return switch (suffix) {
            case "ns" -> 1;
            case "us", "\u03bcs" -> TimeUnit.MICROSECONDS.toNanos(1);
            case "ms" -> TimeUnit.MILLISECONDS.toNanos(1);
            case "s" -> TimeUnit.SECONDS.toNanos(1);
            case "m" -> TimeUnit.MINUTES.toNanos(1);
            case "h" -> TimeUnit.HOURS.toNanos(1);
            case "d" -> TimeUnit.DAYS.toNanos(1);
            default -> throw new NumberFormatException("Invalid duration unit '" + suffix + "'");
        };
    }

    private static String removeEndIgnoreCase(String text, String suffix) {
        return text.toLowerCase().endsWith(suffix.toLowerCase()) ? text.substring(0, text.length() - suffix.length()) : text;
    }

    private static final Pattern DURATION_PATTERN = Pattern.compile("(\\d+(?:\\.\\d+)?)(ns|\u03bcs|us|ms|s|m|h|d)?");
}
