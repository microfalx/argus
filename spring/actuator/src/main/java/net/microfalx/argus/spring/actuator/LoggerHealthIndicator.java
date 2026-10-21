package net.microfalx.argus.spring.actuator;

import net.microfalx.argus.api.LoggerService;
import net.microfalx.lang.RichText;
import org.slf4j.event.Level;
import org.springframework.boot.actuate.health.AbstractHealthIndicator;
import org.springframework.boot.actuate.health.Health;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;

import static net.microfalx.argus.logger.LoggerUtils.countAt;

/**
 * A Spring Boot health indicator exposing recent warning/error log counts.
 */
public class LoggerHealthIndicator extends AbstractHealthIndicator {

    private static final String[] WINDOWS = {"startup", "5m", "1h", "24h"};

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
        details.put("windows", WINDOWS);
        details.put("warnings", toWindowCounts(loggerService, Level.WARN));
        details.put("errors", toWindowCounts(loggerService, Level.ERROR));
        return details;
    }

    private Map<String, Object> toWindowCounts(LoggerService loggerService, Level level) {
        Map<String, Object> values = new LinkedHashMap<>();
        int[] counts = level == Level.WARN ? loggerService.getWarningCounts() : loggerService.getErrorCounts();
        values.put("reportText", loggerService.getReport(level, RichText.Format.TEXT));
        values.put("reportHtml", loggerService.getReport(level, RichText.Format.HTML));
        for (int i = 0; i < WINDOWS.length; i++) {
            values.put(WINDOWS[i], countAt(counts, i));
        }
        return values;
    }


}


