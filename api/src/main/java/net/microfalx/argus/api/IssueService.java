package net.microfalx.argus.api;

import net.microfalx.lang.service.Service;

import java.util.Collection;

/**
 * A service tracking the issues of the current process.
 */
public interface IssueService extends Service {

    static IssueService getInstance() {
        return Service.load(IssueService.class);
    }

    /**
     * Returns pending issues.
     *
     * @return a non-null instance
     */
    Collection<Issue> getPendingIssues();

    /**
     * Registers a new issue.
     *
     * @param issue the issue
     */
    void register(Issue issue);
}
