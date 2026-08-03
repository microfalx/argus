package net.microfalx.argus.report.fragment;

import net.microfalx.argus.api.Alert;
import net.microfalx.argus.api.LoggerService;
import net.microfalx.argus.report.*;

import java.util.Collection;

@net.microfalx.lang.annotation.Provider
public class LoggerProvider extends AbstractFragmentProvider {

    private final TrendHelper trendHelper = new TrendHelper();
    private final ReportHelper reportHelper = new ReportHelper(null);

    @Override
    public Fragment create() {
        return Fragment.builder("Logger").template("logger")
                .icon("fa-regular fa-file-lines")
                .order(900)
                .build();
    }

    @Override
    public void update(Template template) {
        super.update(template);
        template.addVariable("loggerHelper", this);
        template.addVariable("alerts", getAlerts());
        template.addVariable("pendingAlerts", getPendingAlerts());
    }

    /**
     * Returns the application alerts for a given time interval.
     *
     * @return a non-null instance
     */
    public Collection<Alert> getAlerts() {
        Report report = Report.current();
        return report.getAttribute("alerts", this::doGetAlerts);
    }

    /**
     * Returns the application alerts for a given time interval.
     *
     * @return a non-null instance
     */
    public Collection<Alert> getPendingAlerts() {
        Report report = Report.current();
        return report.getAttribute("alerts-pending", () -> doGetAlerts().stream().filter(e -> !e.isAcknowledged()).toList());
    }

    public Chart.PieChart<Integer> getAlertLevelPieChart(String id) {
        Chart.PieChart<Integer> chart = new Chart.PieChart<>(id, "Levels");
        chart.getLegend().setShow(false);
        trendHelper.aggregateInt(getPendingAlerts(), e -> reportHelper.toLabel(e.getLevel()),
                Alert::getPendingEventCount).forEach(chart::add);
        return chart;
    }

    public Chart.PieChart<Integer> getAlertFailureTypePieChart(String id) {
        Chart.PieChart<Integer> chart = new Chart.PieChart<>(id, "Failure Types");
        chart.getLegend().setShow(false);
        trendHelper.aggregateInt(getPendingAlerts(), Alert::getFailureType, Alert::getPendingEventCount)
                .forEach(chart::add);
        return chart;
    }

    private Collection<Alert> doGetAlerts() {
        Report report = Report.current();
        return LoggerService.getInstance().getAlerts(report.getStartTime().toLocalDateTime(),
                report.getEndTime().toLocalDateTime());
    }
}
