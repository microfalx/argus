package net.microfalx.argus.spring.actuator;

import lombok.extern.slf4j.Slf4j;
import net.microfalx.argus.api.HealthService;
import org.springframework.boot.actuate.health.AbstractHealthIndicator;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.Status;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;

/**
 * A Spring Boot health indicator backed by health information.
 */
@Slf4j
public class HealthHealthIndicator extends AbstractHealthIndicator {

    public static final Status IMPACTED = new Status("IMPACTED");

    private final Supplier<HealthService> healthServiceSupplier;

    public HealthHealthIndicator() {
        this(HealthService::getInstance);
    }

    HealthHealthIndicator(Supplier<HealthService> healthServiceSupplier) {
        this.healthServiceSupplier = healthServiceSupplier;
    }

    @Override
    protected void doHealthCheck(Health.Builder builder) throws Exception {
        net.microfalx.argus.api.Health health = resolveHealth();
        if (health == null) {
            builder.unknown().withDetail("reason", "HealthService is unavailable");
        } else {
            builder.status(toStatus(health))
                    .withDetails(toDetails(health));
        }
    }

    private net.microfalx.argus.api.Health resolveHealth() {
        try {
            HealthService healthService = healthServiceSupplier.get();
            if (healthService == null) return null;
            return healthService.getHealth(net.microfalx.argus.api.Health.Type.INSTANCE);
        } catch (Exception e) {
            LOGGER.debug("Failed to obtain health", e);
            return null;
        }
    }

    static Status toStatus(net.microfalx.argus.api.Health health) {
        return switch (health.getSeverity()) {
            case HIGH -> IMPACTED;
            case CRITICAL -> Status.OUT_OF_SERVICE;
            default -> Status.UP;
        };
    }

    static Map<String, Object> toDetails(net.microfalx.argus.api.Health health) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("id", health.getId());
        details.put("type", health.getType().name());
        details.put("severity", health.getSeverity().name());
        details.put("score", health.getScore());
        details.put("createdAt", health.getCreatedAt());
        details.put("modifiedAt", health.getModifiedAt());
        details.put("groups", health.getGroups().size());
        details.put("scored", health.getScored().size());
        details.put("report", health.getReport());
        return details;
    }
}

