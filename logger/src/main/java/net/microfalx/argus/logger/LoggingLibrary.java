package net.microfalx.argus.logger;

/**
 * An interface used to set up the library.
 */
public interface LoggingLibrary {

    /**
     * Configures the library and installs the appenders.
     */
    void install();

    /**
     * Removes the configuration and uninstalls the appenders.
     */
    void uninstall();

    /**
     * Starts the library.
     */
    void start();
}
