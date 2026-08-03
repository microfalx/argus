package net.microfalx.argus.api;

/**
 * An interface to intercept issues and alerts.
 */
public interface IssueListener {

    /**
     * Invoked when an issue is reported.
     *
     * @param issue the issue
     */
    void onIssue(Issue issue);

    /**
     * Invoked when an alert is reported.
     *
     * @param alert the alert
     */
    void onAlert(Alert alert);
}
