package net.microfalx.argus.logger;

import net.microfalx.argus.api.Alert;
import net.microfalx.argus.api.LoggerEvent;
import net.microfalx.argus.api.LoggerListener;
import net.microfalx.argus.api.LoggerService;
import net.microfalx.lang.Initializable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LoggerServiceImplTest {

    private LoggerServiceImpl loggerService;

    @BeforeEach
    void setup() {
        loggerService = new LoggerServiceImpl();
        loggerService.initialize();
        loggerService.clear();
    }

    @Test
    void serviceLocator() {
        assertNotNull(LoggerService.getInstance());
    }

    @Test
    void initialize() {
        assertNotNull(loggerService.getAlerts());
        assertNotNull(loggerService.getListeners());
    }

    @Test
    void register() {
        loggerService.register();
        assertNotNull(loggerService.getAlerts());
        assertNotNull(loggerService.getListeners());
    }

    @Test
    void getAppenders() {
        loggerService.register();
        assertEquals(2, loggerService.getAppenders().size());
    }

    @Test
    void registerAndUnregisterListener() {
        CountingListener listener = new CountingListener();
        loggerService.register(listener);
        assertTrue(loggerService.getListeners().contains(listener));

        loggerService.unregister(listener);
        assertFalse(loggerService.getListeners().contains(listener));
    }

    @Test
    void registerInitializableListener() {
        InitializableListener listener = new InitializableListener();
        loggerService.register(listener);

        assertTrue(listener.initialized.get());
    }

    @Test
    void infoEventDoesNotCreateAlert() {
        String id = "info-" + System.nanoTime();
        loggerService.onEvent(createEvent(id, LoggerEvent.Level.INFO));

        assertNull(loggerService.getAlert(id));
    }

    @Test
    void warnEventCreatesAlert() {
        String id = "warn-" + System.nanoTime();
        loggerService.onEvent(createEvent(id, LoggerEvent.Level.WARN));

        Alert alert = loggerService.getAlert(id);
        assertNotNull(alert);
        assertEquals(id, alert.getId());
        assertFalse(alert.isAcknowledged());
        assertEquals(1, alert.getPendingEventCount());
    }

    @Test
    void acknowledgeUpdatesAlertAndIncludesListenerCount() {
        String id = "ack-" + System.nanoTime();
        loggerService.onEvent(createEvent(id, LoggerEvent.Level.ERROR));
        CountingListener listener = new CountingListener();
        listener.acknowledgeCount = 3;
        loggerService.register(listener);

        long count = loggerService.acknowledge();

        assertTrue(count >= 4);
        Alert alert = loggerService.getAlert(id);
        assertNotNull(alert);
        assertTrue(alert.isAcknowledged());
        assertEquals(0, alert.getPendingEventCount());
    }

    @Test
    void clearIncludesListenerCountAndRemovesCachedAlerts() {
        String id = "clear-" + System.nanoTime();
        loggerService.onEvent(createEvent(id, LoggerEvent.Level.ERROR));
        CountingListener listener = new CountingListener();
        listener.clearCount = 5;
        loggerService.register(listener);

        long count = loggerService.clear();

        assertTrue(count >= 6);
        assertTrue(loggerService.getAlerts().isEmpty());
    }

    private LoggerEvent createEvent(String correlationId, LoggerEvent.Level level) {
        return LoggerEvent.builder()
                .name(getClass().getName())
                .timestamp(System.currentTimeMillis())
                .sequenceNumber(System.nanoTime())
                .threadName(Thread.currentThread().getName())
                .level(level)
                .message("Event for " + correlationId)
                .correlationId(correlationId)
                .build();
    }

    private static class CountingListener implements LoggerListener {

        private long acknowledgeCount;
        private long clearCount;

        @Override
        public void onEvent(LoggerEvent event) {
            // no-op in this test listener
        }

        @Override
        public long acknowledge() {
            return acknowledgeCount;
        }

        @Override
        public long clear() {
            return clearCount;
        }
    }

    private static class InitializableListener extends CountingListener implements Initializable {

        private final AtomicBoolean initialized = new AtomicBoolean();

        @Override
        public void initialize(Object... context) {
            initialized.set(true);
        }

    }

}