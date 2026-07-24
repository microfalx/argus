package net.microfalx.argus.api;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;
import net.microfalx.lang.Nameable;

import static net.microfalx.lang.ArgumentUtils.requireNonNull;
import static net.microfalx.lang.ArgumentUtils.requireNotEmpty;

/**
 * A pair of thresholds used to calculate scores.
 */
@Getter
@ToString
@EqualsAndHashCode
public final class Thresholds implements Nameable {

    /**
     * The name of the thresholds
     */
    private final String name;

    /**
     * The threshold for warning levels
     */
    private final Threshold warn;

    /**
     * The thresholds for error levels
     */
    private final Threshold error;

    /**
     * The start of the range
     */
    private Float minimum;

    /**
     * The end of the range
     */
    private Float maximum;

    private boolean reverse;

    /**
     * Creates a new instance from a warning and an error threshold with unit {@link Unit#COUNTER}.
     *
     * @param warn  the warning threshold
     * @param error the error threshold
     * @return a new instance of {@link Thresholds}
     */
    public static Thresholds create(String name, float warn, float error) {
        return new Thresholds(name, Threshold.warn(warn), Threshold.error(error));
    }

    /**
     * Creates a new instance from a warning and an error threshold.
     *
     * @param warn  the warning threshold
     * @param error the error threshold
     * @return a new instance of {@link Thresholds}
     */
    public static Thresholds create(String name, float warn, float error, Unit unit) {
        return new Thresholds(name, Threshold.warn(warn).withUnit(unit), Threshold.error(error).withUnit(unit));
    }

    /**
     * Creates a new instance from a warning and an error threshold.
     *
     * @param warn  the warning threshold
     * @param error the error threshold
     * @return a new instance of {@link Thresholds}
     */
    public static Thresholds create(String name, Threshold warn, Threshold error) {
        return new Thresholds(name, warn, error);
    }

    private Thresholds(String name, Threshold warn, Threshold error) {
        requireNotEmpty(name);
        requireNonNull(warn);
        requireNonNull(error);
        this.name = name;
        this.warn = warn;
        this.error = error;
        if (error.getValue() < warn.getValue()) {
            throw new IllegalArgumentException("The error threshold (" + error.getValue() + ") cannot be less " +
                    "than warn threshold (" + warn.getValue() + ")");
        }
        if (warn.getUnit() != error.getUnit()) {
            throw new IllegalArgumentException("The error unit (" + error.getUnit() + ") cannot be different " +
                    "than warn threshold (" + warn.getUnit() + ")");
        }
    }

    /**
     * Returns whether the score is calculated in reverse (i.e. lower values are better).
     *
     * @return {@code true} if thresholds are reversed, {@code false} otherwise
     */
    public boolean isReverse() {
        return reverse;
    }

    /**
     * Changes whether the score should be calculated in reverse (i.e. lower values are better).
     *
     * @param reverse {@code true} to reverse the thresholds, {@code false} otherwise
     * @return a new instance of {@link Thresholds} with the specified reverse setting
     */
    public Thresholds withReverse(boolean reverse) {
        Thresholds copy = new Thresholds(name, warn, error);
        copy.reverse = reverse;
        return copy;
    }

    /**
     * Returns the unit associated with these thresholds.
     *
     * @return a non-null instance
     */
    public Unit getUnit() {
        return warn.getUnit();
    }

    /**
     * Returns the minimum range either by calculating it based on levels or returning
     * the pre-configured value.
     *
     * @return a non-null instance
     */
    public Float getMinimum() {
        if (minimum == null) {
            if (getUnit() == Unit.PERCENT) {
                return 0f;
            } else {
                return (reverse ? error.getValue() : warn.getValue()) / 2;
            }
        } else {
            return minimum;
        }
    }

    /**
     * Returns the minimum range either by calculating it based on levels or returning
     * the pre-configured value.
     *
     * @return a non-null instance
     */
    public Float getMaximum() {
        if (maximum == null) {
            if (getUnit() == Unit.PERCENT) {
                return 100f;
            } else {
                return (reverse ? error.getValue() : warn.getValue()) * 2;
            }
        } else {
            return maximum;
        }
    }

    /**
     * Calculates the score based on the thresholds.
     *
     * @param value the value to evaluate
     * @return the score
     */
    public float getScore(float value) {
        float minimum = getMinimum();
        float maximum = getMaximum();
        float warnThreshold = warn.getValue();
        float errorThreshold = error.getValue();

        if (warnThreshold == errorThreshold) {
            return Health.WITH_ISSUES;
        }

        if (!reverse) {
            if (value < minimum) return Health.MAX;
            if (value < warnThreshold) return interpolate(value, minimum, warnThreshold, Health.MAX, Health.WARNING);
            if (value <= warnThreshold) return Health.WARNING;
            if (value < errorThreshold) return interpolate(value, warnThreshold, errorThreshold, Health.WARNING, Health.ERROR);
            if (value <= errorThreshold) return Health.ERROR;
            if (value < maximum) return interpolate(value, errorThreshold, maximum, Health.ERROR, Health.MIN);
            return Health.MIN;
        }

        if (value < minimum) return Health.MIN;
        if (value < warnThreshold) return interpolate(value, minimum, warnThreshold, Health.MIN, Health.WARNING);
        if (value <= warnThreshold) return Health.WARNING;
        if (value < errorThreshold) return interpolate(value, warnThreshold, errorThreshold, Health.WARNING, Health.ERROR);
        if (value <= errorThreshold) return Health.ERROR;
        if (value < maximum) return interpolate(value, errorThreshold, maximum, Health.ERROR, Health.MAX);
        return Health.MAX;
    }

    private static float
    interpolate(float value, float start, float end, float startScore, float endScore) {
        if (start == end) {
            return endScore;
        }
        float t = (value - start) / (end - start);
        return Health.normalize(startScore + (endScore - startScore) * t);
    }


}
