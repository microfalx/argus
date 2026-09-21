package net.microfalx.argus.spring.actuator;

import net.microfalx.argus.api.HealthService;
import net.microfalx.argus.api.Resource;
import org.springframework.boot.actuate.endpoint.annotation.Endpoint;
import org.springframework.boot.actuate.endpoint.annotation.ReadOperation;
import org.springframework.boot.actuate.endpoint.annotation.Selector;

import java.util.*;
import java.util.function.Supplier;

/**
 * Exposes resources and their health through Spring Boot Actuator.
 */
@Endpoint(id = "resource")
public class ResourceEndpoint {

    private final Supplier<HealthService> healthServiceSupplier;

    public ResourceEndpoint() {
        this(HealthService::getInstance);
    }

    ResourceEndpoint(Supplier<HealthService> healthServiceSupplier) {
        this.healthServiceSupplier = healthServiceSupplier;
    }

    @ReadOperation
    public Map<String, Collection<Resource>> resources() {
        HealthService healthService = resolveHealthService();
        Map<String, Collection<Resource>> resources = new LinkedHashMap<>();
        if (healthService == null) return resources;
        for (Resource.Type type : Resource.Type.values()) {
            Collection<Resource> resourcesByType = healthService.getResources(type);
            if (!resourcesByType.isEmpty()) {
                resources.put(type.name().toLowerCase(Locale.ROOT), new ArrayList<>(resourcesByType));
            }
        }
        return resources;
    }

    @ReadOperation
    public Collection<Resource> resources(@Selector String type) {
        HealthService healthService = resolveHealthService();
        if (healthService == null) {
            return java.util.List.of();
        }
        return new ArrayList<>(healthService.getResources(parseType(type)));
    }

    private HealthService resolveHealthService() {
        try {
            return healthServiceSupplier.get();
        } catch (Exception e) {
            return null;
        }
    }

    private Resource.Type parseType(String type) {
        try {
            return Resource.Type.valueOf(type.toUpperCase(Locale.ROOT));
        } catch (Exception e) {
            throw new IllegalArgumentException("Unknown resource type: " + type, e);
        }
    }
}

