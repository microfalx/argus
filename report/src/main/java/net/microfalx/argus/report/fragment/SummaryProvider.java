package net.microfalx.argus.report.fragment;

import net.microfalx.argus.report.AbstractFragmentProvider;
import net.microfalx.argus.report.Fragment;
import net.microfalx.argus.report.TrendHelper;

@net.microfalx.lang.annotation.Provider
public class SummaryProvider extends AbstractFragmentProvider {

    private final TrendHelper trendHelper = new TrendHelper();

    @Override
    public Fragment create() {
        return Fragment.builder("Summary").template("summary")
                .icon("fa-solid fa-list-check")
                .order(10)
                .build();
    }

}
