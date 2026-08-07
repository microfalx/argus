package net.microfalx.argus.logger;

import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.Appender;
import ch.qos.logback.core.rolling.RollingFileAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import static net.microfalx.argus.logger.LoggerUtils.getLoggerContext;
import static net.microfalx.argus.logger.LoggerUtils.getRootLogger;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class LogbackLoggingLibraryTest {

    @TempDir
    Path tempDir;

    private LoggerContext loggerContext;

    @BeforeEach
    void setUp() {
        loggerContext = getLoggerContext();
        resetLoggerContext();
    }

    @AfterEach
    void tearDown() {
        resetLoggerContext();
    }

    @Test
    void addsErrorAppenderToRootLogger() {
        installLibrary();

        assertAppenderPresent(getRootLogger(), "process.error");
    }

    @Test
    void addsWarningAppenderToRootLogger() {
        installLibrary();

        assertAppenderPresent(getRootLogger(), "process.warn");
    }

    @Test
    void addsAllLogsAppenderToRootLogger() {
        installLibrary();

        assertAppenderPresent(getRootLogger(), "process");
    }

    @Test
    void addsOneAppenderPerRegisteredAppender() {
        installLibrary();

        assertSingleAppender("net.microfalx.test1");
        assertSingleAppender("net.microfalx.test2");
    }

    private void installLibrary() {
        LogbackLoggingLibrary library = new LogbackLoggingLibrary();
        library.directory = tempDir.toFile();
        library.install();
    }

    private void resetLoggerContext() {
        LogbackRecorderAppender.release(loggerContext);
        loggerContext.reset();
    }

    private void assertAppenderPresent(ch.qos.logback.classic.Logger logger, String appenderName) {
        Appender<ILoggingEvent> appender = findAppender(logger, appenderName);
        assertNotNull(appender, "Expected appender '" + appenderName + "' to be installed on logger '" + logger.getName() + "'");
        assertInstanceOf(RollingFileAppender.class, appender,
                "Expected appender '" + appenderName + "' to be a rolling file appender");
    }

    private void assertSingleAppender(String loggerName) {
        ch.qos.logback.classic.Logger logger = loggerContext.getLogger(loggerName);
        List<String> appenderNames = appenderNames(logger);
        assertEquals(1, appenderNames.size(), "Expected exactly one appender on logger '" + loggerName + "'");
    }

    private Appender<ILoggingEvent> findAppender(ch.qos.logback.classic.Logger logger, String appenderName) {
        Iterator<Appender<ILoggingEvent>> appenderIterator = logger.iteratorForAppenders();
        while (appenderIterator.hasNext()) {
            Appender<ILoggingEvent> appender = appenderIterator.next();
            if (appenderName.equals(appender.getName())) {
                return appender;
            }
        }
        return null;
    }

    private List<String> appenderNames(ch.qos.logback.classic.Logger logger) {
        List<String> names = new ArrayList<>();
        Iterator<Appender<ILoggingEvent>> appenderIterator = logger.iteratorForAppenders();
        while (appenderIterator.hasNext()) {
            names.add(appenderIterator.next().getName());
        }
        return names;
    }
}


