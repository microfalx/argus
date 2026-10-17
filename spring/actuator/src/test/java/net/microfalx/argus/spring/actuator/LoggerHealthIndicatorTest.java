package net.microfalx.argus.spring.actuator;

import net.microfalx.argus.api.LoggerService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.actuate.health.Status;

import java.lang.reflect.Proxy;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class LoggerHealthIndicatorTest {

    @Test
    void healthExposesWarningAndErrorCounts() {
        LoggerService loggerService = (LoggerService) Proxy.newProxyInstance(
                LoggerService.class.getClassLoader(),
                new Class[]{LoggerService.class},
                (proxy, method, args) -> {
                    if ("equals".equals(method.getName())) return proxy == args[0];
                    if ("hashCode".equals(method.getName())) return System.identityHashCode(proxy);
                    if ("toString".equals(method.getName())) return "LoggerServiceProxy";
                    if ("getWarningCounts".equals(method.getName())) return new int[]{2, 14, 95};
                    if ("getErrorCounts".equals(method.getName())) return new int[]{1, 7, 33};
                    return null;
                }
        );

        LoggerHealthIndicator indicator = new LoggerHealthIndicator(() -> loggerService);

        org.springframework.boot.actuate.health.Health result = indicator.health();

        assertThat(result.getStatus()).isEqualTo(Status.UP);
        assertThat((String[]) result.getDetails().get("windows")).containsExactly("5m", "1h", "24h");
        assertThat(result.getDetails()).containsEntry("warnings", Map.of("5m", 2, "1h", 14, "24h", 95));
        assertThat(result.getDetails()).containsEntry("errors", Map.of("5m", 1, "1h", 7, "24h", 33));
    }

    @Test
    void healthDefaultsToUnknownWhenServiceUnavailable() {
        LoggerHealthIndicator indicator = new LoggerHealthIndicator(() -> null);

        org.springframework.boot.actuate.health.Health result = indicator.health();

        assertThat(result.getStatus()).isEqualTo(Status.UNKNOWN);
        assertThat(result.getDetails()).containsEntry("reason", "Argus LoggerService is unavailable");
    }
}



