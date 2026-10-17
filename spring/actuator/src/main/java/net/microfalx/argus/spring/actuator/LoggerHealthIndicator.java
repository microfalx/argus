package net.microfalx.argus.spring.actuator;

import net.microfalx.argus.api.LoggerService;
import org.springframework.boot.actuate.health.AbstractHealthIndicator;
import org.springframework.boot.actuate.health.Health;

import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;

/**
 * A Spring Boot health indicator exposing recent warning/error log counts.
 */
public class LoggerHealthIndicator extends AbstractHealthIndicator {

    private static final String[] WINDOWS = {"5m", "1h", "24h"};

    private final Supplier<LoggerService> loggerServiceSupplier;

    public LoggerHealthIndicator() {
        this(LoggerService::getInstance);
    }

    LoggerHealthIndicator(Supplier<LoggerService> loggerServiceSupplier) {
        this.loggerServiceSupplier = loggerServiceSupplier;
    }

    @Override
    protected void doHealthCheck(Health.Builder builder) {
        LoggerService loggerService = resolveLoggerService();
        if (loggerService == null) {
            builder.unknown().withDetail("reason", "Argus LoggerService is unavailable");
        } else {
            builder.up().withDetails(toDetails(loggerService));
        }
    }

    private LoggerService resolveLoggerService() {
        try {
            return loggerServiceSupplier.get();
        } catch (Exception e) {
            return null;
        }
    }

    private Map<String, Object> toDetails(LoggerService loggerService) {
        Map<String, Object> details = new LinkedHashMap<>();
        int[] warningCounts = resolveCounts(loggerService, "getWarningCounts");
        int[] errorCounts = resolveCounts(loggerService, "getErrorCounts");
        details.put("windows", WINDOWS);
        details.put("warnings", toWindowCounts(warningCounts));
        details.put("errors", toWindowCounts(errorCounts));
        return details;
    }

    private int[] resolveCounts(LoggerService loggerService, String methodName) {
        try {
            Method method = loggerService.getClass().getMethod(methodName);
            Object counts = method.invoke(loggerService);
            return counts instanceof int[] ? (int[]) counts : new int[0];
        } catch (Exception e) {
            return new int[0];
        }
    }

    private Map<String, Integer> toWindowCounts(int[] counts) {
        Map<String, Integer> values = new LinkedHashMap<>();
        for (int i = 0; i < WINDOWS.length; i++) {
            values.put(WINDOWS[i], countAt(counts, i));
        }
        return values;
    }

    private int countAt(int[] counts, int index) {
        return counts != null && index < counts.length ? counts[index] : 0;
    }
}


