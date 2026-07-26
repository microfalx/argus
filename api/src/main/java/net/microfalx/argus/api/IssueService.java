package net.microfalx.argus.api;

import net.microfalx.lang.service.Service;

/**
 * A service tracking the issues of the current process.
 */
public interface IssueService extends Service {

    static IssueService getInstance() {
        return Service.load(IssueService.class);
    }
}
