package net.microfalx.argus.spring.actuator;

import net.microfalx.argus.api.LoggerService;
import net.microfalx.lang.FormatterUtils;
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
        int[] warningCounts = loggerService.getWarningCounts();
        int[] errorCounts = loggerService.getErrorCounts();
        details.put("windows", WINDOWS);
        details.put("warnings", toWindowCounts(Level.WARN, warningCounts));
        details.put("errors", toWindowCounts(Level.ERROR, errorCounts));
        return details;
    }

    private Map<String, Object> toWindowCounts(Level level, int[] counts) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("reportText", getReportCounts(RichText.Format.TEXT, counts));
        values.put("reportHtml", getReportCounts(RichText.Format.HTML, counts));
        for (int i = 0; i < WINDOWS.length; i++) {
            values.put(WINDOWS[i], countAt(counts, i));
        }
        return values;
    }

    private String getReportCounts(RichText.Format format, int[] counts) {
        return RichText.create()
                .a(FormatterUtils.formatCount(countAt(counts, 0))).a("/")
                .a(FormatterUtils.formatCount(countAt(counts, 1))).a("/")
                .a(FormatterUtils.formatCount(countAt(counts, 2)))
                .render(format);
    }

}


