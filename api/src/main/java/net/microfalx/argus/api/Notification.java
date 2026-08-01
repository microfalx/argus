package net.microfalx.argus.api;

import net.microfalx.lang.NamedIdentityAware;

/**
 * An abstract representation of a notification.
 */
public class Notification extends NamedIdentityAware<String> {

    /**
     * Sends a notification.
     *
     * @return self
     * @see NotificationService#send(Notification)
     */
    public Notification send() {
        NotificationService.getInstance().send(this);
        return this;
    }
}
