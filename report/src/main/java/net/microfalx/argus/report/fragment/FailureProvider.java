package net.microfalx.argus.report.fragment;

import net.microfalx.argus.api.Failure;
import net.microfalx.argus.report.*;

import java.util.Collection;
import java.util.Comparator;

@net.microfalx.lang.annotation.Provider
public class FailureProvider extends AbstractFragmentProvider {

    private final TrendHelper trendHelper = new TrendHelper();
    private final ReportHelper reportHelper = new ReportHelper(null);

    @Override
    public Fragment create() {
        return Fragment.builder("Failures").template("failure")
                .icon("fa-solid fa-bug")
                .order(900)
                .build();
    }

    @Override
    public void update(Template template) {
        super.update(template);
        Collection<Failure.Counts> failuresByType = getFailuresByType();
        Collection<Failure.Counts> failuresByException = getFailuresByException();
        int failuresByExceptionCount = sum(failuresByException);
        template.addVariable("failureHelper", this);
        template.addVariable("failuresCount", failuresByExceptionCount);
        template.addVariable("failuresByType", failuresByType);
        template.addVariable("failuresByException", failuresByException);
    }

    private int sum(Collection<Failure.Counts> failures) {
        return failures.stream().mapToInt(Failure.Counts::getErrorCount).sum();
    }

    private Collection<Failure.Counts> getFailuresByType() {
        return Failure.getTypeCounts().values().stream()
                .sorted(Comparator.comparing(Failure.Counts::getName))
                .toList();
    }

    private Collection<Failure.Counts> getFailuresByException() {
        return Failure.getExceptionCounts().values().stream()
                .sorted(Comparator.comparing(Failure.Counts::getName))
                .toList();
    }

}
