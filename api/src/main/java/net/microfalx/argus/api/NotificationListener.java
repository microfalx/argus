package net.microfalx.argus.api;

/**
 * A listener which processes notifications.
 * <p>
 * Each listener has its own configuration to where the notification is sent. Common implementations are:
 * <ul>
 *     <li>Email</li>
 *     <li>SMS</li>
 * </ul>
 */
public interface NotificationListener {

    /**
     * Invoked when a notification needs to be sent out.
     *
     * @param notification the notification to be sent
     */
    void onNotification(Notification notification);
}
