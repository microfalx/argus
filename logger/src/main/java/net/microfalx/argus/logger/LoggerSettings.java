package net.microfalx.argus.logger;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.net.URI;

@Getter
@Setter
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

    public enum Protocol {
        TCP,
        UDP
    }

    @Getter
    @Setter
    @ToString
    public static class Remote {
        private String hostname;
        private int port = -1;
        private Protocol protocol = Protocol.UDP;
        private String facility = "user";
        private boolean onlyAlerts = true;

        public URI toUri() {
            return URI.create(protocol.name().toLowerCase() + "://" + hostname + ":" + port);
        }
    }


    public static class Syslog extends Remote {

        public Syslog() {
            setPort(2514);
        }
    }


    public static class Gelf extends Remote {

        public Gelf() {
            setPort(12201);
        }
    }
}
