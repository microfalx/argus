package net.microfalx.argus.core;

import lombok.extern.slf4j.Slf4j;
import net.microfalx.argus.api.HealthContributor;
import net.microfalx.argus.api.HealthService;
import net.microfalx.argus.api.Thresholds;
import net.microfalx.argus.api.Unit;
import net.microfalx.configuration.Configuration;
import net.microfalx.configuration.ConfigurationEvent;
import net.microfalx.configuration.ConfigurationListener;
import net.microfalx.lang.ClassUtils;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import static net.microfalx.lang.FormatterUtils.*;
import static net.microfalx.lang.StringUtils.*;

/**
 * Manages the configuration around health thresholds.
 * <p>
 * Each threshold (declared as a {@code static volatile} field in a health contributor) is stored under
 * {@code health.thresholds.<contributor>.<thresholds>} with the value {@code <warn>,<error>}, each value
 * including the unit (like {@code 85%,95%} or {@code 50ms,2s}). The metadata
 * for these entries is provided by {@code configuration.xml} descriptors in each module.
 */
@Slf4j
class HealthConfiguration implements ConfigurationListener {

    public static final String PREFIX = "health.thresholds";
    private static final String SEPARATOR = ",";

    private static final Map<Long, String> DURATION_UNITS = new LinkedHashMap<>();
    private static final Map<Long, String> BYTE_UNITS = new LinkedHashMap<>();

    private final Map<String, Field> fields = new ConcurrentHashMap<>();

    /**
     * Discovers thresholds from health contributors and registers as external configuration.
     */
    void register() {
        getContributors().forEach(this::register);
    }

    /**
     * Updates the thresholds from external configuration and applies to health contributors.
     */
    void update() {
        Configuration configuration = Configuration.get();
        fields.forEach((key, field) -> update(field, key, configuration.get(key)));
    }

    @Override
    public void onEvent(ConfigurationEvent event) {
        if (event.matchesProperties(PREFIX)) {
            Field field = fields.get(event.getKey());
            if (field != null) update(field, event.getKey(), event.getCurrentValue());
        } else if (event.matchesGroup(PREFIX)) {
            update();
        }
    }

    private void register(HealthContributor contributor) {
        Configuration configuration = Configuration.get();
        getFields(contributor).forEach((key, field) -> {
            fields.put(key, field);
            if (isNotEmpty(configuration.get(key))) return;
            Thresholds thresholds = getThresholds(field);
            if (thresholds == null) return;
            configuration.set(key, encode(thresholds));
            LOGGER.debug("Register health thresholds '{}', value '{}'", key, encode(thresholds));
        });
    }

    private void update(Field field, String key, String value) {
        Thresholds thresholds = getThresholds(field);
        if (thresholds == null || isEmpty(value)) return;
        try {
            Thresholds newThresholds = decode(thresholds, value);
            if (newThresholds.getWarn().getValue() == thresholds.getWarn().getValue()
                    && newThresholds.getError().getValue() == thresholds.getError().getValue()) {
                return;
            }
            setThresholds(field, newThresholds);
            LOGGER.info("Update health thresholds '{}', value '{}'", key, value);
        } catch (Exception e) {
            LOGGER.warn("Failed to update health thresholds '{}' with '{}', root cause: {}", key, value, e.getMessage());
        }
    }

    private Collection<HealthContributor> getContributors() {
        return HealthService.getInstance().getContributors();
    }

    private String getKey(HealthContributor contributor, Thresholds thresholds) {
        String name = thresholds.getName();
        String group = thresholds.getGroup().orElse(null);
        String id = group == null || group.equalsIgnoreCase(contributor.getName()) || group.equalsIgnoreCase(name)
                ? toIdentifier(name) : toIdentifier(group, name);
        return PREFIX + "." + toIdentifier(contributor.getName()) + "." + id;
    }

    private String encode(Thresholds thresholds) {
        Unit unit = thresholds.getUnit();
        return encode(unit, thresholds.getWarn().getValue()) + SEPARATOR + encode(unit, thresholds.getError().getValue());
    }

    private Thresholds decode(Thresholds thresholds, String value) {
        String[] parts = split(value, SEPARATOR);
        if (parts.length != 2) {
            throw new IllegalArgumentException("Expected '<warn>" + SEPARATOR + "<error>'");
        }
        Unit unit = thresholds.getUnit();
        return thresholds.with(unit.parse(parts[0]), unit.parse(parts[1]));
    }

    /**
     * Formats the value with its unit, without losing precision (unlike {@link Unit#format(float)}, which
     * is meant for display), so it can be read back with {@link Unit#parse(String)}.
     */
    private String encode(Unit unit, float value) {
        return switch (unit) {
            case PERCENT -> format(value) + "%";
            case MILLISECOND -> encode(value, DURATION_UNITS, 1, "ms");
            case NANOSECOND -> encode(value, DURATION_UNITS, NANOS_IN_MILLI, "ns");
            case BYTE -> encode(value, BYTE_UNITS, 1, "B");
            case THROUGHPUT -> format(value) + "r/s";
            case COUNTER -> format(value);
        };
    }

    private String encode(float value, Map<Long, String> units, long scale, String baseSuffix) {
        if (value == (long) value && value > 0) {
            long scaled = (long) value;
            for (Map.Entry<Long, String> entry : units.entrySet()) {
                long size = entry.getKey() * scale;
                if (scaled >= size && scaled % size == 0) return (scaled / size) + entry.getValue();
            }
        }
        return format(value) + baseSuffix;
    }

    private String format(float value) {
        return value == (long) value ? Long.toString((long) value) : Float.toString(value);
    }

    private Map<String, Field> getFields(HealthContributor contributor) {
        Map<String, Field> fields = new LinkedHashMap<>();
        for (Field field : contributor.getClass().getDeclaredFields()) {
            int modifiers = field.getModifiers();
            if (!Modifier.isStatic(modifiers) || !Thresholds.class.isAssignableFrom(field.getType())) {
                continue;
            }
            if (!Modifier.isVolatile(modifiers)) {
                LOGGER.warn("Health thresholds field '{}.{}' should be volatile", ClassUtils.getName(field.getDeclaringClass()), field.getName());
            }
            Thresholds thresholds = getThresholds(field);
            if (thresholds == null) continue;
            // the first declared field wins (variants like "not available" share the name)
            fields.putIfAbsent(getKey(contributor, thresholds), field);
        }
        return fields;
    }

    private Thresholds getThresholds(Field field) {
        try {
            field.setAccessible(true);
            return (Thresholds) field.get(null);
        } catch (Exception e) {
            LOGGER.warn("Failed to extract health thresholds from '{}.{}', root cause: {}",
                    ClassUtils.getName(field.getDeclaringClass()), field.getName(), e.getMessage());
            return null;
        }
    }

    private void setThresholds(Field field, Thresholds thresholds) throws IllegalAccessException {
        field.setAccessible(true);
        field.set(null, thresholds);
    }

    static {
        // in milliseconds, from the largest unit
        DURATION_UNITS.put(TimeUnit.DAYS.toMillis(1), "d");
        DURATION_UNITS.put(TimeUnit.HOURS.toMillis(1), "h");
        DURATION_UNITS.put(TimeUnit.MINUTES.toMillis(1), "m");
        DURATION_UNITS.put(TimeUnit.SECONDS.toMillis(1), "s");
        DURATION_UNITS.put(1L, "ms");

        BYTE_UNITS.put(G, "GB");
        BYTE_UNITS.put(M, "MB");
        BYTE_UNITS.put(K, "KB");
    }
}
