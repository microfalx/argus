package net.microfalx.argus.logger;

import net.microfalx.argus.api.*;
import net.microfalx.argus.core.contributor.AbstractHealthContributor;
import net.microfalx.lang.JvmUtils;
import net.microfalx.lang.annotation.Order;
import net.microfalx.lang.annotation.Provider;
import net.microfalx.lang.annotation.Tag;

import java.time.Duration;
import java.util.Collection;
import java.util.List;

@Provider
@Order(100)
@Tag("logger")
public class LoggerHealthContributor extends AbstractHealthContributor {

    private static final Duration STARTUP_REPORT_DURATION = Duration.ofMinutes(60);

    private final static String GROUP = "Logger";
    private final static String WARNING_GROUP = "Warning";
    private final static String ERROR_GROUP = "Error";

    @Override
    public String getName() {
        return GROUP;
    }

    @Override
    public boolean supports(Resource.Type type) {
        return type == Resource.Type.SERVICE;
    }

    @Override
    public void update(Health health) {
        LoggerService loggerService = LoggerService.getInstance();
        Health.Group group = health.getGroup(GROUP);

        Health.Group warningGroup = group.getGroup(WARNING_GROUP);
        int[] warningCounts = loggerService.getWarningCounts();
        warningGroup.update(asCounterItem(shouldAlertForStartup() ? WARNING_STARTUP : WARNING_STARTUP_NA, LoggerUtils.countAt(warningCounts, 0)));
        warningGroup.update(asCounterItem(WARNING_5M, LoggerUtils.countAt(warningCounts, 1)));
        warningGroup.update(asCounterItem(WARNING_1H, LoggerUtils.countAt(warningCounts, 2)));
        warningGroup.update(asCounterItem(WARNING_24H, LoggerUtils.countAt(warningCounts, 3)));

        Health.Group errorGroup = group.getGroup(ERROR_GROUP);
        int[] errorCounts = loggerService.getErrorCounts();
        errorGroup.update(asCounterItem(shouldAlertForStartup() ? ERROR_STARTUP : ERROR_STARTUP_NA, LoggerUtils.countAt(errorCounts, 0)));
        errorGroup.update(asCounterItem(ERROR_5M, LoggerUtils.countAt(errorCounts, 1)));
        errorGroup.update(asCounterItem(ERROR_1H, LoggerUtils.countAt(errorCounts, 2)));
        errorGroup.update(asCounterItem(ERROR_24H, LoggerUtils.countAt(errorCounts, 3)));
    }

    @Override
    public Collection<Thresholds> getThresholds() {
        return List.of(WARNING_5M, WARNING_1H, WARNING_24H, ERROR_5M, ERROR_1H, ERROR_24H);
    }

    private boolean shouldAlertForStartup() {
        return JvmUtils.getUptime().compareTo(STARTUP_REPORT_DURATION) <= 0;
    }

    static volatile Thresholds WARNING_STARTUP = Thresholds.create("Startup", 1, 5, Unit.COUNTER)
            .withId("logger.startup.warn").withGroup(WARNING_GROUP);
    static volatile Thresholds WARNING_STARTUP_NA = Thresholds.create("Startup", 0, 0, Unit.COUNTER)
            .withId("logger.startup.warn.na").withGroup(WARNING_GROUP);
    static volatile Thresholds WARNING_5M = Thresholds.create("Last 5m", 5, 10, Unit.COUNTER)
            .withId("logger.5m.warn").withGroup(WARNING_GROUP);
    static volatile Thresholds WARNING_1H = Thresholds.create("Last 1h", 10, 50, Unit.COUNTER)
            .withId("logger.1h.warn").withGroup(WARNING_GROUP);
    static volatile Thresholds WARNING_24H = Thresholds.create("Last 24h", 100, 500, Unit.COUNTER)
            .withId("logger.24h.warn").withGroup(WARNING_GROUP);

    static volatile Thresholds ERROR_STARTUP = Thresholds.create("Startup", 1, 2, Unit.COUNTER)
            .withId("logger.5m.error").withGroup(ERROR_GROUP);
    static volatile Thresholds ERROR_STARTUP_NA = Thresholds.create("Startup", 0, 0, Unit.COUNTER)
            .withId("logger.5m.error").withGroup(ERROR_GROUP);
    static volatile Thresholds ERROR_5M = Thresholds.create("Last 5m", 1, 5, Unit.COUNTER)
            .withId("logger.5m.error").withGroup(ERROR_GROUP);
    static volatile Thresholds ERROR_1H = Thresholds.create("Last 1h", 10, 50, Unit.COUNTER)
            .withId("logger.1h.error").withGroup(ERROR_GROUP);
    static volatile Thresholds ERROR_24H = Thresholds.create("Last 24h", 100, 500, Unit.COUNTER)
            .withId("logger.24h.error").withGroup(ERROR_GROUP);

}
