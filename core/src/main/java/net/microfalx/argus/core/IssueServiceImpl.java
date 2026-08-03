package net.microfalx.argus.core;

import lombok.extern.slf4j.Slf4j;
import net.microfalx.argus.api.Alert;
import net.microfalx.argus.api.Issue;
import net.microfalx.argus.api.IssueListener;
import net.microfalx.argus.api.IssueService;
import net.microfalx.lang.ClassUtils;
import net.microfalx.lang.Initializable;
import net.microfalx.lang.service.Service;
import net.microfalx.threadpool.Trigger;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArraySet;

import static java.util.Collections.unmodifiableCollection;
import static net.microfalx.lang.ArgumentUtils.requireNonNull;
import static net.microfalx.lang.ClassUtils.resolveProviderInstances;
import static net.microfalx.lang.CollectionUtils.immutableCollection;

@Slf4j
public class IssueServiceImpl extends AbstractService implements IssueService, Initializable {

    private final Queue<Issue> issues = new ConcurrentLinkedQueue<>();
    private final Queue<Alert> alerts = new ConcurrentLinkedQueue<>();

    private volatile Collection<IssueListener> listeners = Collections.emptyList();
    private final Collection<IssueListener> registeredListeners = new CopyOnWriteArraySet<>();
    private final Collection<IssueListener> classPathListeners = new CopyOnWriteArraySet<>();

    public static IssueService getInstance() {
        return Service.lookup(IssueService.class);
    }

    @Override
    public Collection<Issue> getPendingIssues() {
        return unmodifiableCollection(issues);
    }

    @Override
    public Collection<Alert> getPendingAlerts() {
        return unmodifiableCollection(alerts);
    }

    @Override
    public Collection<IssueListener> getListeners() {
        return immutableCollection(listeners);
    }

    @Override
    public void register(IssueListener listener) {
        requireNonNull(listener);
        initializeListener(listener);
        registeredListeners.add(listener);
        LOGGER.debug("Register notification listener - {}", ClassUtils.getName(listener));
        updateListeners();
    }

    @Override
    public void unregister(IssueListener listener) {
        requireNonNull(listener);
        registeredListeners.remove(listener);
        LOGGER.debug("Unregister notification listener - {}", ClassUtils.getName(listener));
        updateListeners();
    }

    public void register(Issue issue) {
        requireNonNull(issue);
        issues.offer(issue);
    }

    @Override
    public void register(Alert alert) {
        requireNonNull(alert);
        alerts.offer(alert);
    }

    @Override
    public void initialize(Object... context) {
        discoverListeners();
        updateListeners();
    }

    @Override
    public void start() {
        super.start();
        registerTasks();
    }

    private void fireIssue(Issue issue) {
        for (IssueListener listener : listeners) {
            listener.onIssue(issue);
        }
    }

    private void fireAlert(Alert alert) {
        for (IssueListener listener : listeners) {
            listener.onAlert(alert);
        }
    }

    private void discoverListeners() {
        LOGGER.info("Discover listeners");
        for (IssueListener listener : resolveProviderInstances(IssueListener.class)) {
            LOGGER.debug(" - {}", ClassUtils.getName(listener));
            initializeListener(listener);
            classPathListeners.add(listener);
        }
        LOGGER.info("Discovered {} listeners", classPathListeners.size());
    }

    private void updateListeners() {
        Collection<IssueListener> updatedListeners = new ArrayList<>(classPathListeners);
        updatedListeners.addAll(registeredListeners);
        this.listeners = updatedListeners;
    }

    private void initializeListener(IssueListener listener) {
        if (listener instanceof Initializable) {
            ((Initializable) listener).initialize();
        }
    }

    private void registerTasks() {
        getThreadPool().schedule(new IssueProcessorWorker(), Trigger.fixedDelay(Duration.ofSeconds(5)));
        getThreadPool().schedule(new AlertProcessorWorker(), Trigger.fixedDelay(Duration.ofSeconds(5)));
    }

    private class IssueProcessorWorker implements Runnable {

        @Override
        public void run() {
            for (; ; ) {
                Issue issue = issues.poll();
                if (issue != null) {
                    fireIssue(issue);
                } else {
                    break;
                }
            }
        }
    }

    private class AlertProcessorWorker implements Runnable {

        @Override
        public void run() {
            for (; ; ) {
                Alert alert = alerts.poll();
                if (alert != null) {
                    fireAlert(alert);
                } else {
                    break;
                }
            }
        }
    }
}
