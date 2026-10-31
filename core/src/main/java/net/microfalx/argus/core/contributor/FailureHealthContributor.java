package net.microfalx.argus.core.contributor;

import net.microfalx.argus.api.*;
import net.microfalx.lang.EnumUtils;
import net.microfalx.lang.annotation.Order;
import net.microfalx.lang.annotation.Provider;
import net.microfalx.lang.annotation.Tag;
import net.microfalx.metrics.statistics.TrendStatisticalSummary;

import java.util.Collection;
import java.util.List;

@Provider
@Order(60)
@Tag("failure")
public class FailureHealthContributor extends AbstractHealthContributor {

    private final static String GROUP = "Failures";

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
        Health.Group group = health.getGroup(GROUP);
        Health.Group warningsGroup = group.getGroup("Warnings");
        update(warningsGroup, Failure.Severity.WARNING);
        Health.Group errorsGroup = group.getGroup("Errors");
        update(errorsGroup, Failure.Severity.ERROR);
    }

    private void update(Health.Group group, Failure.Severity severity) {
        IssueService issueService = IssueService.getInstance();
        Thresholds thresholds = severity == Failure.Severity.ERROR ? ERROR : WARNING;
        for (Failure.Type type : Failure.Type.values()) {
            TrendStatisticalSummary trend = issueService.getTrend(severity, type);
            group.update(asCounterItem(thresholds, (int) trend.getMean())
                    .withName(EnumUtils.toLabel(type))
                    .withPolicy(Health.Policy.REPORT_IF_HAS_VALUE));
        }
    }

    @Override
    public Collection<Thresholds> getThresholds() {
        return List.of(WARNING, ERROR);
    }

    private static volatile Thresholds WARNING = Thresholds.create("Warning", 200, 500, Unit.COUNTER)
            .withId("alert.warn").withGroup(GROUP);
    private static volatile Thresholds ERROR = Thresholds.create("Error", 10, 200, Unit.COUNTER)
            .withId("alert.error").withGroup(GROUP);
}
