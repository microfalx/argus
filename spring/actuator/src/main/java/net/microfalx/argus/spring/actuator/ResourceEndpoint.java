package net.microfalx.argus.spring.actuator;

import net.microfalx.argus.api.HealthService;
import net.microfalx.argus.api.Resource;
import org.springframework.boot.actuate.endpoint.annotation.Endpoint;
import org.springframework.boot.actuate.endpoint.annotation.ReadOperation;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
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
    public Map<String, Collection<ResourceMetadata>> getResources() {
        HealthService healthService = resolveHealthService();
        Map<String, Collection<ResourceMetadata>> resources = new LinkedHashMap<>();
        if (healthService == null) return resources;
        for (Resource.Type type : Resource.Type.values()) {
            Collection<Resource> resourcesByType = healthService.getResources(type);
            if (!resourcesByType.isEmpty()) {
                resources.put(type.name(), resourcesByType.stream().map(ResourceMetadata::of).toList());
            }
        }
        return resources;
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

