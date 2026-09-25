package net.microfalx.argus.spring.actuator;

import net.microfalx.argus.api.Health;
import net.microfalx.argus.api.HealthService;
import net.microfalx.argus.api.Resource;
import org.junit.jupiter.api.Test;

import java.util.Collection;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ResourceEndpointTest {

    @Test
    void resourcesAreReturnedGroupedByType() {
        Health health = new Health("service");
        health.update("General", "cpu", 8f);
        Resource resource = Resource.create(Resource.Type.SERVICE, "svc-1").withHealth(health);

        HealthService healthService = mock(HealthService.class);
        for (Resource.Type type : Resource.Type.values()) {
            when(healthService.getResources(type)).thenReturn(List.of());
        }
        when(healthService.getResources(Resource.Type.SERVICE)).thenReturn(List.of(resource));

        ResourceEndpoint endpoint = new ResourceEndpoint(() -> healthService);

        Map<String, Collection<Resource>> allResources = endpoint.getResources();

        assertThat(allResources).containsKey("service");
        assertThat(allResources.get("service")).hasSize(1);
        assertThat(allResources.get("service").iterator().next().getHealth()).isNotNull();
        assertThat(endpoint.getResources("service")).hasSize(1);
    }

    @Test
    void resourcesAreEmptyWhenServiceUnavailable() {
        ResourceEndpoint endpoint = new ResourceEndpoint(() -> null);

        assertThat(endpoint.getResources()).isEmpty();
        assertThat(endpoint.getResources("service")).isEmpty();
    }
}

