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

    private boolean debug;
    private boolean trace;
    private int fileCount = 5;
    private long fileSize = 20_000_000L;
    private String directory;
    private String application;
    private String process;
    private Syslog syslog = new Syslog();
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
