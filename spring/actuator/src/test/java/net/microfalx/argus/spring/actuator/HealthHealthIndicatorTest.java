package net.microfalx.argus.spring.actuator;

import net.microfalx.argus.api.Health;
import net.microfalx.argus.api.HealthService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.actuate.health.Status;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class HealthHealthIndicatorTest {

    @Test
    void healthIsMappedToUp() {
        Health health = new Health("service");
        health.update("General", "cpu", 8f);
        HealthService healthService = mock(HealthService.class);
        when(healthService.getHealth(Health.Type.INSTANCE)).thenReturn(health);

        HealthHealthIndicator indicator = new HealthHealthIndicator(() -> healthService);

        org.springframework.boot.actuate.health.Health result = indicator.health();

        assertThat(result.getStatus()).isEqualTo(Status.UP);
        assertThat(result.getDetails()).containsEntry("severity", "OK");
        assertThat(result.getDetails()).containsEntry("type", Health.Type.INSTANCE.name());
    }

    @Test
    void healthIsMappedToDownForCriticalIssues() {
        Health health = new Health("service");
        health.update("General", "cpu", 2f);
        HealthService healthService = mock(HealthService.class);
        when(healthService.getHealth(Health.Type.INSTANCE)).thenReturn(health);

        HealthHealthIndicator indicator = new HealthHealthIndicator(() -> healthService);

        org.springframework.boot.actuate.health.Health result = indicator.health();

        assertThat(result.getStatus()).isEqualTo(Status.DOWN);
        assertThat(result.getDetails()).containsEntry("severity", "HIGH");
    }

    @Test
    void healthDefaultsToUnknownWhenServiceUnavailable() {
        HealthHealthIndicator indicator = new HealthHealthIndicator(() -> null);

        org.springframework.boot.actuate.health.Health result = indicator.health();

        assertThat(result.getStatus()).isEqualTo(Status.UNKNOWN);
        assertThat(result.getDetails()).containsEntry("reason", "Argus HealthService is unavailable");
    }
}

