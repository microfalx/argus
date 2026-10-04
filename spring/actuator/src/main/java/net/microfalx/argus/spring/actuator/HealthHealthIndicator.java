package net.microfalx.argus.spring.actuator;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import net.microfalx.argus.api.HealthService;
import net.microfalx.argus.api.HealthSettings;
import net.microfalx.lang.HttpServletUtils;
import org.springframework.boot.actuate.health.AbstractHealthIndicator;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.Status;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;

/**
 * A Spring Boot health indicator backed by health information.
 */
@Slf4j
public class HealthHealthIndicator extends AbstractHealthIndicator {

    public static final Status IMPACTED = new Status("IMPACTED");
    private static final String REPORT_PATH = "/support/report";

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

    private HealthSettings getSettings() {
        try {
            HealthService healthService = healthServiceSupplier.get();
            if (healthService != null) {
                return healthService.getSettings();
            }
        } catch (Exception e) {
            LOGGER.debug("Failed to obtain health settings", e);
        }
        return new HealthSettings();
    }

    static Status toStatus(net.microfalx.argus.api.Health health) {
        return switch (health.getSeverity()) {
            case HIGH -> IMPACTED;
            case CRITICAL -> Status.OUT_OF_SERVICE;
            default -> Status.UP;
        };
    }

    private Map<String, Object> toDetails(net.microfalx.argus.api.Health health) {
        Map<String, Object> details = new LinkedHashMap<>();
        URI reportUri = resolveReportPath();
        HealthMetadata healthMetadata = HealthMetadata.of(health);
        details.put("id", healthMetadata.getId());
        details.put("type", healthMetadata.getType().name());
        details.put("severity", healthMetadata.getSeverity().name());
        details.put("score", healthMetadata.getScore());
        details.put("createdAt", healthMetadata.getCreatedAt());
        details.put("modifiedAt", healthMetadata.getModifiedAt());
        details.put("age", healthMetadata.getAge());
        details.put("groups", healthMetadata.getGroups());
        details.put("items", healthMetadata.getItems());
        details.put("report", healthMetadata.getReport());
        details.put("reportPath", reportUri.getPath());
        HttpServletRequest request = currentRequest();
        if (HttpServletUtils.isClientLocal(request)) {
            details.put("reportToken", getSettings().getReportToken());
        }
        return details;
    }

    private URI resolveReportPath() {
        return ServletUriComponentsBuilder.fromCurrentContextPath().path(REPORT_PATH).build().toUri();
    }

    private static HttpServletRequest currentRequest() {
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        return attributes != null ? attributes.getRequest() : null;
    }
}
