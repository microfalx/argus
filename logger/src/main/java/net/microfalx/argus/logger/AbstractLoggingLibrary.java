package net.microfalx.argus.logger;

import net.microfalx.argus.api.LoggerListener;

import java.io.File;
import java.util.Collection;

import static java.util.Collections.unmodifiableCollection;

public abstract class AbstractLoggingLibrary implements LoggingLibrary {

    LoggerListener listener;
    String hostname;
    LoggerSettings settings = new LoggerSettings();
    Collection<Appender> appenders;
    File directory;

    protected final LoggerSettings getSettings() {
        return settings;
    }

    protected final Collection<Appender> getAppenders() {
        return unmodifiableCollection(appenders);
    }

    protected final File getDirectory() {
        return directory;
    }
}
