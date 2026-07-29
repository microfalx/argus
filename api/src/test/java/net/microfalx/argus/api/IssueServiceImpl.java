package net.microfalx.argus.api;

import com.google.common.util.concurrent.AbstractService;
import net.microfalx.lang.annotation.Provider;

import java.util.Collection;
import java.util.List;

@Provider
public class IssueServiceImpl extends AbstractService implements IssueService {

    @Override
    protected void doStart() {

    }

    @Override
    protected void doStop() {

    }

    @Override
    public Collection<Issue> getPendingIssues() {
        return List.of();
    }

    @Override
    public void register(Issue issue) {

    }
}
