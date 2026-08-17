package net.microfalx.argus.core.contributor;

import net.microfalx.argus.api.*;
import net.microfalx.lang.annotation.Provider;
import net.microfalx.lang.annotation.Tag;

import java.util.Collection;
import java.util.List;

@Provider
@Tag("alert")
public class AlertHealthContributor extends AbstractHealthContributor {

    private final static String GROUP = "Alerts";

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
        IssueService issueService = IssueService.getInstance();
        group.update(asCounterItem(WARNING, (int) issueService.getTrend(Alert.Severity.WARN).getMean()));
        group.update(asCounterItem(ERROR, (int) issueService.getTrend(Alert.Severity.ERROR).getMean()));
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
