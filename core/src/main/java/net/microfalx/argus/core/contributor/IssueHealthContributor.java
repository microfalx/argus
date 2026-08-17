package net.microfalx.argus.core.contributor;

import net.microfalx.argus.api.*;
import net.microfalx.lang.annotation.Provider;
import net.microfalx.lang.annotation.Tag;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

@Provider
@Tag("issue")
public class IssueHealthContributor extends AbstractHealthContributor {

    private final static String GROUP = "Issues";

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
        for (Issue.Type type : getTypes()) {
            Thresholds thresholds = getThresholds(type);
            group.update(asCounterItem(thresholds, (int) issueService.getTrend(type).getMean()));
        }
    }

    @Override
    public Collection<Thresholds> getThresholds() {
        return List.of();
    }

    private Issue.Type[] getTypes() {
        return Arrays.stream(Issue.Type.values()).filter(type -> type != Issue.Type.SECURITY)
                .collect(Collectors.toSet()).toArray(Issue.Type[]::new);
    }

    private Thresholds getThresholds(Issue.Type type) {
        return switch (type) {
            case SECURITY -> SECURITY;
            case DOS -> DOS;
            default -> OTHER;
        };
    }


    private static volatile Thresholds SECURITY = Thresholds.create("Security", 5, 10, Unit.COUNTER)
            .withId("issue.security").withGroup(GROUP);
    private static volatile Thresholds DOS = Thresholds.create("Denial of Service", 5, 10, Unit.COUNTER)
            .withId("issue.dos").withGroup(GROUP);
    private static volatile Thresholds OTHER = Thresholds.create("Other", 10, 20, Unit.COUNTER)
            .withId("issue.other").withGroup(GROUP);


}
