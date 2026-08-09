package net.microfalx.argus.api;

import lombok.*;

import java.io.File;
import java.net.URI;

import static net.microfalx.lang.ArgumentUtils.requireNonNull;

/**
 * Settings for loggers.
 */
@Getter
@With
@NoArgsConstructor
@AllArgsConstructor
@ToString
public class LoggerSettings {

    /**
     * Indicates whether the DEBUG statements are logged across all appenders.
     */
    private boolean debug;

    /**
     * Indicates whether the TRACE statements are logged across all appenders.
     */
    private boolean trace;

    /**
     * The number of files to be kept for each file appender.
     */
    private int fileCount = 5;

    /**
     * Returns the maximum file size of a file within one appender.
     */
    private long fileSize = 20_000_000L;

    /**
     * The location of the logs.
     * <p>
     * If missing, it will be auto-detected
     */
    private String directory;

    /**
     * The name of the application, which is sent to the external logs aggregation systems
     */
    private String application;

    /**
     * The name of the process/service, which is sent to the external logs aggregation systems
     */
    private String process;

    /**
     * The settings for the receiver where syslog events are sent.
     */
    private Syslog syslog = new Syslog();

    /**
     * The settings for the receiver where the GELF event are sent.
     */
    private Gelf gelf = new Gelf();

    public static LoggerSettings create(File directory) {
        requireNonNull(directory);
        LoggerSettings loggerSettings = new LoggerSettings();
        loggerSettings.directory = directory.getAbsolutePath();
        return loggerSettings;
    }

    public enum Protocol {
        TCP,
        UDP
    }

    /**
     * Contract for all remote systems used to receive logs from application.
     */
    public static abstract class Remote {

        /**
         * Returns the hostname of the log receiver.
         *
         * @return a non-null instance
         */
        public abstract String getHostname();

        /**
         * Returns the port of the log receiver.
         *
         * @return a non-null instance
         */
        public abstract int getPort();

        /**
         * Returns the protocol used to send logs.
         *
         * @return a non-null instance
         */
        public abstract Protocol getProtocol();

        public URI toUri() {
            return URI.create(getProtocol().name().toLowerCase() + "://" + getHostname() + ":" + getPort());
        }
    }

    @Getter
    @With
    @NoArgsConstructor
    @AllArgsConstructor
    @ToString
    public static class Syslog extends Remote {

        private String hostname;
        private int port = -1;
        private Protocol protocol = Protocol.UDP;
        private String facility = "user";
        private boolean onlyAlerts = true;

    }

    @Getter
    @With
    @NoArgsConstructor
    @AllArgsConstructor
    @ToString
    public static class Gelf extends Remote {

        private String hostname;
        private int port = -1;
        private Protocol protocol = Protocol.UDP;
        private String facility = "user";
        private boolean onlyAlerts = true;

    }
}
