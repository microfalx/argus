package net.microfalx.argus.logger;

import java.io.File;
import java.util.Collection;

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
}
