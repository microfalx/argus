package net.microfalx.argus.api;

import lombok.*;

import static net.microfalx.lang.ArgumentUtils.requireNonNull;

/**
 * Holds a threshold value and its type (warning or error).
 */
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
@Getter
@ToString
@EqualsAndHashCode
public final class Threshold {

    /**
     * The type of the threshold
     */
    private final Type type;

    /**
     * The value of the threshold
     */
    private final float value;

    /**
     * The unit of measurement
     */
    private Unit unit = Unit.COUNTER;

    /**
     * Creates a warning threshold.
     *
     * @param value the value of the threshold
     * @return a non-null instance
     */
    public static Threshold warn(float value) {
        return create(Type.WARN, value);
    }

    /**
     * Creates an error threshold.
     *
     * @param value the value of the threshold
     * @return a non-null instance
     */
    public static Threshold error(float value) {
        return create(Type.ERROR, value);
    }

    /**
     * Creates a new threshold
     *
     * @param type  the type of the threshold
     * @param value the value of the threshold
     * @return a non-null instance
     */
    public static Threshold create(Type type, float value) {
        return new Threshold(type, value);
    }

    /**
     * Changes the unit of the threshold
     *
     * @param unit the new unit
     * @return a new instance with a different threshold
     */
    public Threshold withUnit(Unit unit) {
        requireNonNull(unit);
        Threshold newThreshold = new Threshold(type, value);
        newThreshold.unit = unit;
        return newThreshold;
    }

    public enum Type {
        WARN,
        ERROR
    }
}
