package net.microfalx.argus.api;

import net.microfalx.lang.service.Service;
import net.microfalx.metrics.statistics.TrendStatisticalSummary;

import java.util.Collection;

/**
 * A service tracking the issues of the current process.
 */
public interface IssueService extends Service {

    static IssueService getInstance() {
        return Service.lookup(IssueService.class);
    }

    /**
     * Returns registered notification listeners.
     * <p>
     * The complete list is based on listeners discovered from class path and those registered
     * with {@link #register(IssueListener)} .
     *
     * @return a non-null collection
     */
    Collection<IssueListener> getListeners();

    /**
     * Registers a listener.
     *
     * @param listener the listener
     */
    void register(IssueListener listener);

    /**
     * Unregisters a listener.
     *
     * @param listener the listener
     */
    void unregister(IssueListener listener);

    /**
     * Returns pending issues.
     *
     * @return a non-null instance
     */
    Collection<Issue> getPendingIssues();

    /**
     * Returns pending alerts.
     *
     * @return a non-null instance
     */
    Collection<Alert> getPendingAlerts();

    /**
     * Registers a new issue.
     *
     * @param issue the issue
     */
    void register(Issue issue);

    /**
     * Registers a new alert.
     *
     * @param alert the alert
     */
    void register(Alert alert);

    /**
     * Returns a statistical summary of the trend of the alerts with the given severity.
     *
     * @param severity the severity
     * @return a non-null instance
     */
    TrendStatisticalSummary getTrend(Alert.Severity severity);

    /**
     * Returns a statistical summary of the trend of the issues with the given type.
     *
     * @param type the issue type
     * @return a non-null instance
     */
    TrendStatisticalSummary getTrend(Issue.Type type);
}
