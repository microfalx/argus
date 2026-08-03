package net.microfalx.argus.logger;

import biz.paluch.logging.gelf.logback.GelfLogbackAppender;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.net.SyslogAppender;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.Appender;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import net.microfalx.argus.api.Alert;
import net.microfalx.argus.api.LoggerEvent;
import net.microfalx.argus.api.LoggerListener;
import net.microfalx.argus.api.LoggerService;
import net.microfalx.argus.core.AbstractService;
import net.microfalx.lang.*;
import net.microfalx.lang.annotation.Provider;
import net.microfalx.resource.Resource;
import net.microfalx.store.api.Query;
import net.microfalx.store.api.Store;
import net.microfalx.store.api.StoreService;
import org.slf4j.LoggerFactory;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.atomic.AtomicLong;

import static net.microfalx.argus.logger.LoggerUtils.*;
import static net.microfalx.lang.ArgumentUtils.requireNonNull;
import static net.microfalx.lang.ClassUtils.resolveProviderInstances;
import static net.microfalx.lang.CollectionUtils.immutableCollection;
import static net.microfalx.lang.StringUtils.defaultIfNull;

@Slf4j
@Provider
public class LoggerServiceImpl extends AbstractService implements LoggerService, Initializable, LoggerListener {

    /**
     * Holds the settings to control the behaviour of the service.
     */
    @Getter
    @Setter
    private LoggerSettings settings = new LoggerSettings();

    private String hostname;

    private final Map<String, Alert> alerts = new ConcurrentHashMap<>();
    private final Queue<LoggerEvent> lastLoggerEvents = new ArrayBlockingQueue<>(100);

    private Store<LoggerEvent, Long> store;
    private Store<Alert, String> alertStore;

    private volatile Collection<LoggerListener> listeners = Collections.emptyList();
    private final Collection<LoggerListener> registeredListeners = new CopyOnWriteArraySet<>();
    private final Collection<LoggerListener> classPathListeners = new CopyOnWriteArraySet<>();

    /**
     * Returns the registered listeners.
     *
     * @return a non-null instance
     */
    public Collection<LoggerListener> getListeners() {
        return immutableCollection(listeners);
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
        return CollectionUtils.immutableCollection(alerts.values());
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
        return CollectionUtils.immutableCollection(lastLoggerEvents);
    }

    public void register() {
        discoverListeners();
        updateListeners();
        registerLogBackAppender();
        registerAppenders();
    }

    @Override
    public void initialize(Object... context) {
        initHostInformation();
        initializeStores();
        initializeListeners();
        initializeAppenders();
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
            LoggerUtils.METRICS_FAILURE.increment(ExceptionUtils.getRootCauseName(e));
        }
    }

    private void registerLogBackAppender() {
        LoggerContext loggerContext = (LoggerContext) LoggerFactory.getILoggerFactory();
        RecorderAppender.initialize(loggerContext);
    }

    private void registerAppenders() {
        ApplicationAppenders appenders = new ApplicationAppenders();
        appenders.register();
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

    private void initializeAppenders() {
        initializeApplicationAppender();
        initializeGelfAppender();
        initializeSyslogAppender();
    }

    private void initializeApplicationAppender() {
        ApplicationAppenders appenders = new ApplicationAppenders();
        if (appenders.hasLogsDirectory()) {
            LOGGER.info("Use logs directory: {}", appenders.getLogsDirectory().getAbsolutePath());
        }
        ch.qos.logback.classic.Logger logger = getRootLogger();
        Iterator<Appender<ILoggingEvent>> appenderIterator = logger.iteratorForAppenders();
        while (appenderIterator.hasNext()) {
            Appender<ILoggingEvent> appender = appenderIterator.next();
            if (appender instanceof RecorderAppender internalAppender) {
                internalAppender.storage = this;
                processQueuedLoggerEvents(internalAppender.pendingEvents);
            }
        }
    }

    private void initializeGelfAppender() {
        LoggerSettings.Gelf gelf = settings.getGelf();
        if (StringUtils.isEmpty(gelf.getHostname())) return;
        LOGGER.info("Send logs using GELF to '{}'", gelf.toUri());
        LoggerContext loggerContext = (LoggerContext) LoggerFactory.getILoggerFactory();
        GelfLogbackAppender appender = new GelfLogbackAppender();
        appender.setHost(gelf.getHostname());
        appender.setPort(gelf.getPort());
        appender.setIncludeFullMdc(true);
        appender.setIncludeLocation(true);
        appender.setExtractStackTrace("true");
        appender.setOriginHost(hostname);
        appender.setFacility(gelf.getFacility());
        appender.setAdditionalFields("Application=" + defaultIfNull(settings.getApplication(), "Bootstrap"));
        appender.setAdditionalFields("Process=" + defaultIfNull(settings.getProcess(), "Web"));
        appender.setContext(loggerContext);
        appender.setName("gelf");
        appender.start();
        ch.qos.logback.classic.Logger logger = loggerContext.getLogger(ch.qos.logback.classic.Logger.ROOT_LOGGER_NAME);
        logger.addAppender(appender);
    }

    private void initializeSyslogAppender() {
        LoggerSettings.Syslog syslog = settings.getSyslog();
        if (StringUtils.isEmpty(syslog.getHostname())) return;
        LOGGER.info("Send logs using Syslog to '{}'", syslog.toUri());
        LoggerContext loggerContext = (LoggerContext) LoggerFactory.getILoggerFactory();
        SyslogAppender appender = new SyslogAppender();
        appender.setSyslogHost(syslog.getHostname());
        appender.setPort(syslog.getPort());
        appender.setFacility(syslog.getFacility());
        appender.setThrowableExcluded(true);
        appender.setContext(loggerContext);
        appender.setName("syslog");
        appender.start();
        ch.qos.logback.classic.Logger logger = loggerContext.getLogger(ch.qos.logback.classic.Logger.ROOT_LOGGER_NAME);
        logger.addAppender(appender);
    }

    private void initializeTasks() {
        getThreadPool().submit(new AcknowledgeAlertsTask());
        getThreadPool().submit(new ArchiveLogsTask());
    }

    private void trackLogEvents(LoggerEvent event) {
        METRICS_COUNTS_SEVERITY.count(event.getLevel().name());
        if (event.getExceptionClassName() != null) METRICS_COUNTS_EXCEPTION.count(event.getExceptionClassName());
    }

    private void processQueuedLoggerEvents(Queue<LoggerEvent> events) {
        for (; ; ) {
            LoggerEvent event = events.poll();
            if (event == null) break;
            onEvent(event);
        }
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
            LoggerUtils.METRICS_EVENT_STORE_FAILURE.increment(ExceptionUtils.getRootCauseName(e));
        }
        for (LoggerListener listener : listeners) {
            try {
                listener.onEvent(event);
            } catch (Throwable e) {
                LOGGER.debug("Failed to store logging event '{}' with listener {}", event, ClassUtils.getName(listener));
                LoggerUtils.METRICS_EVENT_STORE_FAILURE.increment(ExceptionUtils.getRootCauseName(e));
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
            LoggerUtils.METRICS_ALERT_STORE_FAILURE.increment(ExceptionUtils.getRootCauseName(e));
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
            ApplicationAppenders appenders = new ApplicationAppenders();
            appenders.move(logs);
        }
    }

}
