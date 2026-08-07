package net.microfalx.argus.logger;

import ch.qos.logback.classic.LoggerContext;
import net.microfalx.argus.api.LoggerEvent;
import net.microfalx.argus.api.LoggerListener;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests that verify logback logging events are properly passed to the LoggerListener.
 */
class LogbackRecorderAppenderTest {

    private LoggerContext loggerContext;
    private TestLoggerListener testListener;
    private Logger logger;

    @BeforeEach
    void setup() {
        // Get the logback logger context
        loggerContext = (LoggerContext) LoggerFactory.getILoggerFactory();
        // Clear any previous appender installation
        loggerContext.removeObject(LogbackRecorderAppender.INSTALLED_FLAG);
        // Initialize the recorder appender
        LogbackRecorderAppender.initialize(loggerContext);
        // Create and install test listener
        testListener = new TestLoggerListener();
        // Inject the listener into the appender
        injectListenerIntoAppender(testListener);
        // Get a logger to test with
        logger = LoggerFactory.getLogger(LogbackRecorderAppenderTest.class);
    }

    /**
     * Test that a simple INFO level log message is captured and passed to the listener.
     */
    @Test
    void simpleInfoLogging() {
        String testMessage = "Test info message";
        logger.info(testMessage);

        assertFalse(testListener.getEvents().isEmpty(), "Logger listener should have received an event");

        LoggerEvent event = testListener.getEvents().get(0);
        assertEquals(LoggerEvent.Level.INFO, event.getLevel());
        assertEquals(testMessage, event.getMessage());
        assertEquals(LogbackRecorderAppenderTest.class.getName(), event.getName());
        assertNotNull(event.getId());
        assertTrue(event.getTimestamp() > 0);
        assertNotNull(event.getThreadName());
        assertTrue(event.getThreadName().length() > 0);
    }

    /**
     * Test that multiple log messages are all captured.
     */
    @Test
    void multipleLogMessages() {
        logger.info("First message");
        logger.warn("Second message");
        logger.error("Third message");

        assertEquals(3, testListener.getEvents().size(), "Should capture all three log messages");

        assertEquals(LoggerEvent.Level.INFO, testListener.getEvents().get(0).getLevel());
        assertEquals("First message", testListener.getEvents().get(0).getMessage());
        assertEquals(LoggerEvent.Level.WARN, testListener.getEvents().get(1).getLevel());
        assertEquals("Second message", testListener.getEvents().get(1).getMessage());
        assertEquals(LoggerEvent.Level.ERROR, testListener.getEvents().get(2).getLevel());
        assertEquals("Third message", testListener.getEvents().get(2).getMessage());
    }

    /**
     * Test that exceptions are captured in the log event.
     */
    @Test
    void exceptionLogging() {
        IOException testException = new IOException("Test exception");
        logger.error("Error occurred", testException);

        assertFalse(testListener.getEvents().isEmpty());

        LoggerEvent event = testListener.getEvents().get(0);
        assertEquals(LoggerEvent.Level.ERROR, event.getLevel());
        assertEquals("Error occurred", event.getMessage());
        assertNotNull(event.getExceptionClassName());
        assertEquals("java.io.IOException", event.getExceptionClassName());
        assertNotNull(event.getExceptionStackTrace());
        assertTrue(event.getExceptionStackTrace().contains("java.io.IOException: Test exception"),
                "Exception stack trace should contain exception details");
    }

    /**
     * Test all log levels are properly captured.
     */
    @Test
    void allLogLevels() {
        logger.trace("Trace level");
        logger.debug("Debug level");
        logger.info("Info level");
        logger.warn("Warn level");
        logger.error("Error level");

        assertEquals(5, testListener.getEvents().size());

        assertEquals(LoggerEvent.Level.TRACE, testListener.getEvents().get(0).getLevel());
        assertEquals(LoggerEvent.Level.DEBUG, testListener.getEvents().get(1).getLevel());
        assertEquals(LoggerEvent.Level.INFO, testListener.getEvents().get(2).getLevel());
        assertEquals(LoggerEvent.Level.WARN, testListener.getEvents().get(3).getLevel());
        assertEquals(LoggerEvent.Level.ERROR, testListener.getEvents().get(4).getLevel());
    }

    /**
     * Test that events have unique IDs.
     */
    @Test
    void eventUniqueIds() {
        logger.info("Message 1");
        logger.info("Message 2");
        logger.info("Message 3");

        assertEquals(3, testListener.getEvents().size());

        long id1 = testListener.getEvents().get(0).getId();
        long id2 = testListener.getEvents().get(1).getId();
        long id3 = testListener.getEvents().get(2).getId();

        assertNotEquals(id1, id2, "Each event should have a unique ID");
        assertNotEquals(id2, id3, "Each event should have a unique ID");
        assertNotEquals(id1, id3, "Each event should have a unique ID");
    }

    /**
     * Test that correlation IDs are generated for events.
     */
    @Test
    void correlationIdGeneration() {
        logger.info("Test message");

        LoggerEvent event = testListener.getEvents().getFirst();
        assertNotNull(event.getCorrelationId(), "Correlation ID should be generated");
        assertTrue(!event.getCorrelationId().isEmpty(), "Correlation ID should not be empty");
    }

    /**
     * Test that events have proper timestamp values.
     */
    @Test
    void eventTimestamps() {
        long beforeLog = System.currentTimeMillis();
        logger.info("Test message");
        long afterLog = System.currentTimeMillis();

        LoggerEvent event = testListener.getEvents().getFirst();
        assertTrue(event.getTimestamp() > 0);
        assertTrue(event.getTimestamp() >= beforeLog, "Event timestamp should be after log call");
        assertTrue(event.getTimestamp() <= afterLog, "Event timestamp should be before now");
    }

    /**
     * Test that pending events are processed when listener is set.
     */
    @Test
    void pendingEventsProcessing() {
        // Remove the listener temporarily
        clearListenerFromAppender();

        // Log while no listener is active (events go to pending queue)
        logger.info("Pending message 1");
        logger.info("Pending message 2");

        // Should have no events yet
        assertEquals(0, testListener.getEvents().size());

        // Now inject the listener which should process pending events
        injectListenerIntoAppender(testListener);

        // Pending events should now be in the listener
        assertEquals(2, testListener.getEvents().size(),
                "Pending events should be processed when listener is attached");
        assertEquals("Pending message 1", testListener.getEvents().get(0).getMessage());
        assertEquals("Pending message 2", testListener.getEvents().get(1).getMessage());
    }

    /**
     * Test formatted message handling.
     */
    @Test
    void formattedMessageHandling() {
        String name = "John";
        int age = 30;
        logger.info("User {} is {} years old", name, age);

        LoggerEvent event = testListener.getEvents().get(0);
        assertEquals("User John is 30 years old", event.getMessage());
    }

    /**
     * Injects a test listener into the LogbackRecorderAppender.
     */
    private void injectListenerIntoAppender(LoggerListener listener) {
        ch.qos.logback.classic.Logger rootLogger = loggerContext.getLogger(ch.qos.logback.classic.Logger.ROOT_LOGGER_NAME);
        var appenderIterator = rootLogger.iteratorForAppenders();

        while (appenderIterator.hasNext()) {
            var appender = appenderIterator.next();
            if (appender instanceof LogbackRecorderAppender recorderAppender) {
                recorderAppender.storage = listener;
                // Process any pending events
                LoggerEvent event;
                while ((event = recorderAppender.pendingEvents.poll()) != null) {
                    listener.onEvent(event);
                }
                break;
            }
        }
    }

    /**
     * Removes the listener from the LogbackRecorderAppender.
     */
    private void clearListenerFromAppender() {
        ch.qos.logback.classic.Logger rootLogger = loggerContext.getLogger(ch.qos.logback.classic.Logger.ROOT_LOGGER_NAME);
        var appenderIterator = rootLogger.iteratorForAppenders();

        while (appenderIterator.hasNext()) {
            var appender = appenderIterator.next();
            if (appender instanceof LogbackRecorderAppender recorderAppender) {
                recorderAppender.storage = null;
                recorderAppender.pendingEvents.clear();
                break;
            }
        }
    }

    /**
     * A simple test implementation of LoggerListener that stores all received events.
     */
    static class TestLoggerListener implements LoggerListener {
        private final List<LoggerEvent> events = new ArrayList<>();

        @Override
        public void onEvent(LoggerEvent event) {
            events.add(event);
        }

        List<LoggerEvent> getEvents() {
            return events;
        }
    }
}

