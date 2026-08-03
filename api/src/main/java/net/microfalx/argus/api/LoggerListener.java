package net.microfalx.argus.api;

import net.microfalx.lang.annotation.Provider;

/**
 * An interface used to store logger events.
 */
@Provider
public interface LoggerListener {

    /**
     * Stores a log event in the store.
     *
     * @param event the log event
     */
    void onEvent(LoggerEvent event);

    /**
     * Acknowledges all stored log events (if the listener stores the log entries).
     *
     * @return the number of acknowledged log events
     */
    default long acknowledge() {
        return 0;
    }

    /**
     * Clears all stored log events (if the listener stores the log entries).
     *
     * @return the number of items cleared
     */
    default long clear() {
        return 0;
    }
}
