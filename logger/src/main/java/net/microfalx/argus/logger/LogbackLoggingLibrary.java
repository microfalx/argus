package net.microfalx.argus.logger;

import biz.paluch.logging.gelf.logback.GelfLogbackAppender;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.encoder.PatternLayoutEncoder;
import ch.qos.logback.classic.filter.LevelFilter;
import ch.qos.logback.classic.net.SyslogAppender;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.FileAppender;
import ch.qos.logback.core.rolling.FixedWindowRollingPolicy;
import ch.qos.logback.core.rolling.RollingFileAppender;
import ch.qos.logback.core.rolling.SizeBasedTriggeringPolicy;
import ch.qos.logback.core.spi.FilterReply;
import ch.qos.logback.core.util.Duration;
import ch.qos.logback.core.util.FileSize;
import lombok.extern.slf4j.Slf4j;
import net.microfalx.argus.api.LoggerEvent;
import net.microfalx.argus.api.LoggerSettings;
import net.microfalx.lang.StringUtils;
import net.microfalx.lang.annotation.Provider;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.Iterator;
import java.util.Queue;

import static net.microfalx.argus.logger.LoggerUtils.getLoggerContext;
import static net.microfalx.argus.logger.LoggerUtils.getRootLogger;
import static net.microfalx.lang.FormatterUtils.formatBytes;
import static net.microfalx.lang.StringUtils.defaultIfNull;

@Provider(dependsOn = "ch.qos.logback.classic.LoggerContext")
@Slf4j
public class LogbackLoggingLibrary extends AbstractLoggingLibrary {

    @Override
    public void install() {
        registerLogBackAppender();
        initializeGelfAppender();
        initializeSyslogAppender();
        addStandardAppenders();
        addRegisteredAppenders();
    }

    @Override
    public void start() {
        processQueuedLoggerEvents();
    }

    @Override
    public void uninstall() {

    }

    private void registerLogBackAppender() {
        LoggerContext loggerContext = (LoggerContext) LoggerFactory.getILoggerFactory();
        LogbackRecorderAppender.initialize(loggerContext);
    }

    private void addStandardAppenders() {
        // log everything in one file, easier to track
        registerAllAppender();
        // log all warnings in one file
        net.microfalx.argus.logger.Appender warnAppender = net.microfalx.argus.logger.Appender.builder("process.warn").build();
        registerAppender(warnAppender, Level.WARN);
        // log all errors in one file
        net.microfalx.argus.logger.Appender errorAppender = net.microfalx.argus.logger.Appender.builder("process.error").build();
        registerAppender(errorAppender, Level.ERROR);
    }

    private void addRegisteredAppenders() {
        LoggerLoader loader = new LoggerLoader();
        loader.load();
        LOGGER.debug("Loaded {}", loader.getAppenders().size() + " from descriptors");
        LoggerContext loggerContext = getLoggerContext();
        for (net.microfalx.argus.logger.Appender appender : loader.getAppenders()) {
            FileAppender<ILoggingEvent> fileAppender = createAppender(appender, loggerContext);
            for (String included : appender.getIncluded()) {
                loggerContext.getLogger(included).addAppender(fileAppender);
            }
        }
    }

    private void registerAllAppender() {
        LoggerContext loggerContext = getLoggerContext();
        net.microfalx.argus.logger.Appender allAppender = net.microfalx.argus.logger.Appender.builder("process").build();
        FileAppender<ILoggingEvent> fileAppender = createAppender(allAppender, loggerContext);
        getRootLogger().addAppender(fileAppender);
    }

    private void registerAppender(net.microfalx.argus.logger.Appender appender, Level level) {
        LoggerContext loggerContext = getLoggerContext();
        ch.qos.logback.classic.Logger logger = getRootLogger();
        FileAppender<ILoggingEvent> fileAppender = createAppender(appender, loggerContext);
        LevelFilter filter = new LevelFilter();
        filter.setLevel(level);
        filter.setOnMatch(FilterReply.ACCEPT);
        filter.setOnMismatch(FilterReply.DENY);
        filter.start();
        fileAppender.addFilter(filter);
        logger.addAppender(fileAppender);
    }

    private FileAppender<ILoggingEvent> createAppender(net.microfalx.argus.logger.Appender appender, LoggerContext context) {
        PatternLayoutEncoder layoutEncoder = createLayoutEncoder(context);

        FixedWindowRollingPolicy rollingPolicy = createRollingPolicy(appender, context);
        SizeBasedTriggeringPolicy<ILoggingEvent> triggeringPolicy = createTriggeringPolicy(context);

        RollingFileAppender<ILoggingEvent> fileAppender = new RollingFileAppender<>();
        fileAppender.setContext(context);
        fileAppender.setImmediateFlush(true);
        fileAppender.setBufferSize(FileSize.valueOf("8KB"));
        fileAppender.setFile(new File(getDirectory(), appender.getFileName()).getAbsolutePath());
        fileAppender.setRollingPolicy(rollingPolicy);
        fileAppender.setTriggeringPolicy(triggeringPolicy);
        fileAppender.setName(appender.getName());
        fileAppender.setEncoder(layoutEncoder);

        rollingPolicy.setParent(fileAppender);
        rollingPolicy.start();

        fileAppender.start();

        return fileAppender;
    }

    private FixedWindowRollingPolicy createRollingPolicy(net.microfalx.argus.logger.Appender appender, LoggerContext context) {
        FixedWindowRollingPolicy rollingPolicy = new FixedWindowRollingPolicy();
        rollingPolicy.setContext(context);
        rollingPolicy.setMinIndex(1);
        rollingPolicy.setMaxIndex(getSettings().getFileCount());
        rollingPolicy.setFileNamePattern(".%i.log.gz");
        return rollingPolicy;
    }

    private SizeBasedTriggeringPolicy<ILoggingEvent> createTriggeringPolicy(LoggerContext context) {
        SizeBasedTriggeringPolicy<ILoggingEvent> triggeringPolicy = new SizeBasedTriggeringPolicy<>();
        triggeringPolicy.setContext(context);
        triggeringPolicy.setMaxFileSize(FileSize.valueOf(formatBytes(getSettings().getFileSize())));
        triggeringPolicy.setCheckIncrement(Duration.buildByMinutes(60));
        triggeringPolicy.start();
        return triggeringPolicy;
    }

    private PatternLayoutEncoder createLayoutEncoder(LoggerContext context) {
        PatternLayoutEncoder encoder = new PatternLayoutEncoder();
        encoder.setContext(context);
        encoder.setPattern("%d{yyyy-MM-dd HH:mm:ss.SSSXXX} %-5level [%-15thread] %logger{-36} : %msg%n");
        encoder.start();
        return encoder;
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

    private void processQueuedLoggerEvents() {
        ch.qos.logback.classic.Logger logger = getRootLogger();
        Iterator<ch.qos.logback.core.Appender<ILoggingEvent>> appenderIterator = logger.iteratorForAppenders();
        while (appenderIterator.hasNext()) {
            ch.qos.logback.core.Appender<ILoggingEvent> appender = appenderIterator.next();
            if (appender instanceof LogbackRecorderAppender internalAppender) {
                internalAppender.storage = listener;
                processQueuedLoggerEvents(internalAppender.pendingEvents);
            }
        }
    }

    private void processQueuedLoggerEvents(Queue<LoggerEvent> events) {
        for (; ; ) {
            LoggerEvent event = events.poll();
            if (event == null) break;
            listener.onEvent(event);
        }
    }
}
