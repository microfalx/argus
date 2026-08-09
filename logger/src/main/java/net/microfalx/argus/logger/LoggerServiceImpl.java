package net.microfalx.argus.logger;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.microfalx.argus.api.*;
import net.microfalx.argus.core.AbstractService;
import net.microfalx.lang.ClassUtils;
import net.microfalx.lang.Initializable;
import net.microfalx.lang.JvmUtils;
import net.microfalx.lang.annotation.Provider;
import net.microfalx.resource.Resource;
import net.microfalx.store.api.Query;
import net.microfalx.store.api.Store;
import net.microfalx.store.api.StoreService;

import java.io.File;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.atomic.AtomicLong;

import static net.microfalx.argus.logger.LoggerUtils.METRICS_COUNTS_EXCEPTION;
import static net.microfalx.argus.logger.LoggerUtils.METRICS_COUNTS_SEVERITY;
import static net.microfalx.lang.ArgumentUtils.requireNonNull;
import static net.microfalx.lang.ClassUtils.resolveProviderInstances;
import static net.microfalx.lang.CollectionUtils.immutableCollection;
import static net.microfalx.lang.ExceptionUtils.getRootCauseName;

@Slf4j
@Provider
public class LoggerServiceImpl extends AbstractService implements LoggerService, Initializable, LoggerListener {

    private LoggerSettings settings = new LoggerSettings();

    private final LoggerManager loggerManager = new LoggerManager(this);
    private final Map<String, Alert> alerts = new ConcurrentHashMap<>();
    private final Queue<LoggerEvent> lastLoggerEvents = new ArrayBlockingQueue<>(100);
    private final Collection<Appender> appenders = new CopyOnWriteArrayList<>();

    private Store<LoggerEvent, Long> store;
    private Store<Alert, String> alertStore;

    private volatile Collection<LoggerListener> listeners = Collections.emptyList();
    private final Collection<LoggerListener> registeredListeners = new CopyOnWriteArraySet<>();
    private final Collection<LoggerListener> classPathListeners = new CopyOnWriteArraySet<>();

    /**
     * The hostname of the server where the process runs.
     */
    @Getter
    private String hostname;

    @Override
    public LoggerSettings getSettings() {
        return settings;
    }

    @Override
    public void setSettings(LoggerSettings settings) {
        requireNonNull(settings);
        this.settings = settings;
    }

    /**
     * Returns the registered listeners.
     *
     * @return a non-null instance
     */
    public Collection<LoggerListener> getListeners() {
        return immutableCollection(listeners);
    }

    /**
     * Loads and returns the appenders created by this service in the active logging library.
     *
     * @return a non-null instance
     */
    public Collection<Appender> getAppenders() {
        return immutableCollection(appenders);
    }

    @Override
    public Optional<File> getDirectory() {
        return loggerManager.hasLogsDirectory() ? Optional.of(loggerManager.getLogsDirectory()) : Optional.empty();
    }

    @Override
    public void register(LoggerListener listener) {
        requireNonNull(listener);
        initializeListener(listener);
        registeredListeners.add(listener);
        LOGGER.debug("Register listener - {}", ClassUtils.getName(listener));
        updateListeners();
    }

    @Override
    public void unregister(LoggerListener listener) {
        requireNonNull(listener);
        registeredListeners.remove(listener);
        LOGGER.debug("Unregister listener - {}", ClassUtils.getName(listener));
        updateListeners();
    }

    @Override
    public Collection<Alert> getAlerts() {
        return immutableCollection(alerts.values());
    }

    public Collection<Alert> getAlerts(LocalDateTime start, LocalDateTime end) {
        return alertStore.list(Query.<Alert>builder().start(start).end(end).build());
    }

    public Alert getAlert(String id) {
        requireNonNull(id);
        return alertStore.find(id);
    }

    @Override
    public Collection<LoggerEvent> getLoggerEvents() {
        return immutableCollection(lastLoggerEvents);
    }

    public void register() {
        discoverListeners();
        updateListeners();
        registerAppenders();
    }

    @Override
    public void initialize(Object... context) {
        initHostInformation();
        initializeStores();
        initializeListeners();
        initializeTasks();
    }

    /**
     * Registers a logger listener.
     *
     * @param loggerListener the listener
     */
    public void registerLoggerListener(LoggerListener loggerListener) {
        requireNonNull(loggerListener);
        if (!(loggerListener instanceof LoggerServiceImpl)) {
            LOGGER.info("Logger listener '{}'", ClassUtils.getName(loggerListener));
            classPathListeners.add(loggerListener);
        }
    }

    /**
     * Clears all alerts.
     */
    public long clear() {
        long count = alertStore.clear();
        for (LoggerListener listener : listeners) {
            count += listener.clear();
        }
        alerts.clear();
        return count;
    }

    /**
     * Acknowledge pending alerts.
     */
    public long acknowledge() {
        final AtomicLong count = new AtomicLong(0);
        Query<Alert> query = Query.<Alert>builder().start(LocalDateTime.now().minusDays(7)).build();
        alertStore.update(query, event -> {
            event.setAcknowledged(true);
            event.setPendingEventCount(0);
            count.incrementAndGet();
            return true;
        });
        for (LoggerListener listener : listeners) {
            count.addAndGet(listener.acknowledge());
        }
        alerts.clear();
        return count.get();
    }

    @Override
    public void onEvent(LoggerEvent event) {
        requireNonNull(event);
        try {
            trackLogEvents(event);
            processLogEvent(event);
            processAlertEvent(event);
            forwardLogEvent(event);
        } catch (Throwable e) {
            LoggerUtils.METRICS_FAILURE.increment(getRootCauseName(e));
        }
    }

    private void loadRegisteredAppenders() {
        this.appenders.clear();
        LoggerLoader loader = new LoggerLoader();
        loader.load();
        this.appenders.addAll(loader.getAppenders());
    }

    private void registerAppenders() {
        loadRegisteredAppenders();
        if (loggerManager.hasLogsDirectory()) {
            LOGGER.info("Use logs directory: {}", loggerManager.getLogsDirectory());
        }
        loggerManager.register();
    }

    private void initializeListeners() {
        ClassUtils.resolveProviderInstances(LoggerListener.class).forEach(this::registerLoggerListener);
        updateListeners();
    }

    private void initHostInformation() {
        try {
            hostname = InetAddress.getLocalHost().getCanonicalHostName();
        } catch (UnknownHostException e) {
            hostname = "localhost";
        }
    }

    private void initializeStores() {
        Store.Options options = Store.Options.create(LoggerUtils.LOGGER_STORE, "Logger");
        store = StoreService.getInstance().register(options);
        options = Store.Options.create(LoggerUtils.ALERT_STORE, "Alert");
        alertStore = StoreService.getInstance().register(options);
    }

    private void initializeTasks() {
        getThreadPool().submit(new AcknowledgeAlertsTask());
        getThreadPool().submit(new ArchiveLogsTask());
    }

    private void trackLogEvents(LoggerEvent event) {
        METRICS_COUNTS_SEVERITY.count(event.getLevel().name());
        if (event.getExceptionClassName() != null) METRICS_COUNTS_EXCEPTION.count(event.getExceptionClassName());
    }


    private void forwardLogEvent(LoggerEvent event) {
        for (LoggerListener listener : listeners) {
            try {
                listener.onEvent(event);
            } catch (Throwable e) {
                String listenerClassName = ClassUtils.getName(listener);
                LOGGER.debug("Failed to forward logging event to '{}', event {}", listenerClassName, event);
                LoggerUtils.METRICS_FORWARD_FAILURE.increment(listenerClassName);
            }
        }
    }

    private void processLogEvent(LoggerEvent event) {
        try {
            store.add(event);
        } catch (Throwable e) {
            LOGGER.debug("Failed to store logging event '{}' to internal storage", event);
            LoggerUtils.METRICS_EVENT_STORE_FAILURE.increment(getRootCauseName(e));
        }
        for (LoggerListener listener : listeners) {
            try {
                listener.onEvent(event);
            } catch (Throwable e) {
                LOGGER.debug("Failed to store logging event '{}' with listener {}", event, ClassUtils.getName(listener));
                LoggerUtils.METRICS_EVENT_STORE_FAILURE.increment(getRootCauseName(e));
            }
        }

    }

    private void processAlertEvent(LoggerEvent event) {
        if (event.getLevel().isLowerSeverity(LoggerEvent.Level.WARN)) return;
        Alert alert = getAlert(event);
        alert.update(event);
        storeAlert(alert);
    }

    private void storeAlert(Alert event) {
        try {
            alertStore.add(event);
        } catch (Throwable e) {
            LOGGER.debug("Failed to store alert event '{}' to internal storage", event);
            LoggerUtils.METRICS_ALERT_STORE_FAILURE.increment(getRootCauseName(e));
        }
    }

    private Alert getAlert(LoggerEvent event) {
        return alerts.computeIfAbsent(event.getCorrelationId(), s -> {
            Alert alert = alertStore.find(s);
            if (alert == null) alert = Alert.builder().id(s).build();
            return alert;
        });
    }

    private void discoverListeners() {
        classPathListeners.clear();
        LOGGER.info("Discover listeners");
        for (LoggerListener listener : resolveProviderInstances(LoggerListener.class)) {
            LOGGER.debug(" - {}", ClassUtils.getName(listener));
            initializeListener(listener);
            classPathListeners.add(listener);
        }
        LOGGER.info("Discovered {} listeners", classPathListeners.size());
    }

    private void updateListeners() {
        Collection<LoggerListener> updatedListeners = new ArrayList<>(classPathListeners);
        updatedListeners.addAll(registeredListeners);
        this.listeners = updatedListeners;
    }

    private void initializeListener(LoggerListener listener) {
        if (listener instanceof Initializable) {
            ((Initializable) listener).initialize();
        }
    }

    class AcknowledgeAlertsTask implements Runnable {

        @Override
        public void run() {
            acknowledge();
        }
    }

    class ArchiveLogsTask implements Runnable {

        @Override
        public void run() {
            Resource logs = Resource.directory(JvmUtils.getVariableDirectory()).get("logs", Resource.Type.DIRECTORY);
            LoggerManager appenders = new LoggerManager(LoggerServiceImpl.this);
            appenders.move(logs);
        }
    }

}
