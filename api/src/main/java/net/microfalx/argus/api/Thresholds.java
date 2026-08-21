package net.microfalx.argus.api;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;
import net.microfalx.lang.NamedIdentityAware;

import java.util.Optional;

import static java.util.Optional.ofNullable;
import static net.microfalx.argus.api.Health.*;
import static net.microfalx.lang.ArgumentUtils.requireNonNull;
import static net.microfalx.lang.ArgumentUtils.requireNotEmpty;
import static net.microfalx.lang.StringUtils.toIdentifier;

/**
 * A pair of thresholds used to calculate scores.
 * <p>
 * A lower value generates a high score (close to {@link Health#MAX}) and a higher value
 * generates a low score (close to {@link Health#MIN}).
 */
@Getter
@ToString
@EqualsAndHashCode(callSuper = true)
public final class Thresholds extends NamedIdentityAware<String> {

    /**
     * The threshold for warning levels
     */
    private Threshold warn;

    /**
     * The thresholds for error levels
     */
    private Threshold error;

    /**
     * The group these thresholds belong.
     */
    private String group;

    /**
     * The start of the range
     */
    private Float minimum;

    /**
     * The end of the range
     */
    private Float maximum;

    /**
     * Flag, indicates how the thresholds are applied
     */
    private boolean reverse;

    /**
     * Creates a new instance from a warning and an error threshold with unit {@link Unit#COUNTER}.
     *
     * @param warn  the warning threshold
     * @param error the error threshold
     * @return a new instance of {@link Thresholds}
     */
    public static Thresholds create(String name, float warn, float error) {
        return new Thresholds(toIdentifier(name), name, Threshold.warn(warn), Threshold.error(error));
    }

    /**
     * Creates a new instance from a warning and an error threshold.
     *
     * @param warn  the warning threshold
     * @param error the error threshold
     * @return a new instance of {@link Thresholds}
     */
    public static Thresholds create(String name, float warn, float error, Unit unit) {
        return new Thresholds(toIdentifier(name), name, Threshold.warn(warn).withUnit(unit), Threshold.error(error).withUnit(unit));
    }

    /**
     * Creates a new instance from a warning and an error threshold.
     *
     * @param warn  the warning threshold
     * @param error the error threshold
     * @return a new instance of {@link Thresholds}
     */
    public static Thresholds create(String name, Threshold warn, Threshold error) {
        return new Thresholds(toIdentifier(name), name, warn, error);
    }

    private Thresholds(String id, String name, Threshold warn, Threshold error) {
        requireNotEmpty(id);
        requireNotEmpty(name);
        requireNonNull(warn);
        requireNonNull(error);
        this.setId(id);
        this.setName(name);
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
     * Returns an optional group name these thresholds belong to.
     *
     * @return an optional group name
     */
    public Optional<String> getGroup() {
        return ofNullable(group);
    }

    /**
     * Returns whether the score is calculated in reverse (i.e. higher values are better).
     *
     * @return {@code true} if thresholds are reversed, {@code false} otherwise
     */
    public boolean isReverse() {
        return reverse;
    }

    /**
     * Changes whether the score should be calculated in reverse (i.e. higher values are better).
     *
     * @param reverse {@code true} to reverse the thresholds, {@code false} otherwise
     * @return a new instance of {@link Thresholds} with the specified reverse setting
     */
    public Thresholds withReverse(boolean reverse) {
        Thresholds copy = (Thresholds) copy();
        copy.reverse = reverse;
        return copy;
    }

    /**
     * Changes the identifier of the thresholds.
     *
     * @param id the new identifier
     * @return a new instance of {@link Thresholds} with the specified reverse setting
     */
    public Thresholds withId(String id) {
        requireNonNull(id);
        Thresholds copy = (Thresholds) copy();
        copy.setId(id);
        return copy;
    }

    /**
     * Changes the group name of the thresholds.
     *
     * @param group the group name
     * @return a new instance of {@link Thresholds} with the specified reverse setting
     */
    public Thresholds withGroup(String group) {
        requireNonNull(group);
        Thresholds copy = (Thresholds) copy();
        copy.group = group;
        return copy;
    }


    /**
     * Changes the threshold values.
     *
     * @param warn  the warning level
     * @param error the error level
     * @return a new instance of {@link Thresholds} with the specified threshold values
     */
    public Thresholds with(float warn, float error) {
        Thresholds copy = (Thresholds) copy();
        copy.warn = Threshold.warn(warn).withUnit(getUnit());
        copy.error = Threshold.error(error).withUnit(getUnit());
        copy.maximum = null;
        copy.minimum = null;
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
                minimum = 0f;
            } else {
                minimum = (reverse ? error.getValue() : warn.getValue()) / 2;
            }
        }
        return minimum;
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
                maximum = 100f;
            } else {
                maximum = (reverse ? error.getValue() : warn.getValue()) * 2;
            }
        }
        return maximum;
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
        if (warnThreshold == errorThreshold) return NA;
        if (reverse) {
            if (value <= minimum) return MIN;
            if (value < warnThreshold) return interpolate(value, minimum, warnThreshold, MIN, ERROR);
            if (value <= warnThreshold) return ERROR;
            if (value < errorThreshold) return interpolate(value, warnThreshold, errorThreshold, ERROR, WARNING);
            if (value <= errorThreshold) return WARNING;
            if (value < maximum) return interpolate(value, errorThreshold, maximum, WARNING, MAX);
            return MAX;
        } else {
            if (value <= minimum) return MAX;
            if (value < warnThreshold) return interpolate(value, minimum, warnThreshold, MAX, WARNING);
            if (value <= warnThreshold) return WARNING;
            if (value < errorThreshold) return interpolate(value, warnThreshold, errorThreshold, WARNING, ERROR);
            if (value <= errorThreshold) return ERROR;
            if (value < maximum) return interpolate(value, errorThreshold, maximum, ERROR, MIN);
            return MIN;
        }
    }

    /**
     * Registers the threshold.
     *
     * @return self
     * @see HealthService#register(Thresholds)
     */
    public Thresholds register() {
        HealthService.getInstance().register(this);
        return this;
    }


    private static float
    interpolate(float value, float start, float end, float startScore, float endScore) {
        if (start == end) return endScore;
        float t = (value - start) / (end - start);
        return Health.normalize(startScore + (endScore - startScore) * t);
    }


}
