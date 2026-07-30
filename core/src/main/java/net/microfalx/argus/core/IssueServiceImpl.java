package net.microfalx.argus.core;

import net.microfalx.argus.api.Issue;
import net.microfalx.argus.api.IssueService;
import net.microfalx.lang.service.Service;

import java.util.Collection;
import java.util.Collections;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

import static net.microfalx.lang.ArgumentUtils.requireNonNull;

public class IssueServiceImpl extends AbstractService implements IssueService {

    private final Queue<Issue> issues = new ConcurrentLinkedQueue<>();

    public static IssueService getInstance() {
        return Service.lookup(IssueService.class);
    }

    @Override
    public Collection<Issue> getPendingIssues() {
        return Collections.unmodifiableCollection(issues);
    }

    public void register(Issue issue) {
        requireNonNull(issue);
        issues.offer(issue);
    }

    @Override
    public void initialize(Object... context) {

    }
}
