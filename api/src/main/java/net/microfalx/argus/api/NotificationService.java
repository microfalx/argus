package net.microfalx.argus.api;

import net.microfalx.lang.service.Service;

import java.util.Collection;

/**
 * A service responsible to forward notifications to various channels (email, SMS, etc.).
 */
public interface NotificationService extends Service {

    static NotificationService getInstance() {
        return Service.lookup(NotificationService.class);
    }

    /**
     * Returns registered notification listeners.
     * <p>
     * The complete list is based on listeners discovered from class path and those registered
     * with {@link #register(NotificationListener)} .
     *
     * @return a non-null collection
     */
    Collection<NotificationListener> getListeners();

    /**
     * Registers a listener.
     *
     * @param listener the listener
     */
    void register(NotificationListener listener);

    /**
     * Unregisters a listener.
     *
     * @param listener the listener
     */
    void unregister(NotificationListener listener);

    /**
     * Sends a notification to registered listeners
     *
     * @param notification the notification
     */
    void send(Notification notification);
}
