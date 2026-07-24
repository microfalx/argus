package net.microfalx.argus.api;

import net.microfalx.lang.Service;

public interface IssueService extends Service {

    static IssueService getInstance() {
        return Service.load(IssueService.class);
    }
}
