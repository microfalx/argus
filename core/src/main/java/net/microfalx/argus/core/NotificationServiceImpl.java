package net.microfalx.argus.core;

import lombok.extern.slf4j.Slf4j;
import net.microfalx.argus.api.Notification;
import net.microfalx.argus.api.NotificationListener;
import net.microfalx.argus.api.NotificationService;
import net.microfalx.lang.ClassUtils;
import net.microfalx.lang.Initializable;
import net.microfalx.lang.annotation.Provider;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.concurrent.CopyOnWriteArraySet;

import static net.microfalx.lang.ArgumentUtils.requireNonNull;
import static net.microfalx.lang.ClassUtils.resolveProviderInstances;
import static net.microfalx.lang.CollectionUtils.immutableCollection;

@Provider
@Slf4j
public class NotificationServiceImpl extends AbstractService implements NotificationService {

    private volatile Collection<NotificationListener> listeners = Collections.emptyList();
    private final Collection<NotificationListener> registeredListeners = new CopyOnWriteArraySet<>();
    private final Collection<NotificationListener> classPathListeners = new CopyOnWriteArraySet<>();

    @Override
    public Collection<NotificationListener> getListeners() {
        return immutableCollection(listeners);
    }

    @Override
    public void register(NotificationListener listener) {
        requireNonNull(listener);
        initializeListener(listener);
        registeredListeners.add(listener);
        LOGGER.debug("Register notification listener - {}", ClassUtils.getName(listener));
        updateListeners();
    }

    @Override
    public void unregister(NotificationListener listener) {
        requireNonNull(listener);
        registeredListeners.remove(listener);
        LOGGER.debug("Unregister notification listener - {}", ClassUtils.getName(listener));
        updateListeners();
    }

    @Override
    public void send(Notification notification) {
        requireNonNull(notification);
        for (NotificationListener listener : listeners) {
            try {
                listener.onNotification(notification);
            } catch (Exception e) {
                LOGGER.atError().setCause(e).log("Error sending notification to listener - {}", ClassUtils.getName(listener));
            }
        }
    }

    @Override
    public void initialize(Object... context) {
        discoverListeners();
        updateListeners();
    }

    private void discoverListeners() {
        LOGGER.info("Discover health contributors");
        for (NotificationListener listener : resolveProviderInstances(NotificationListener.class)) {
            LOGGER.debug(" - {}", ClassUtils.getName(listener));
            initializeListener(listener);
            classPathListeners.add(listener);
        }
        LOGGER.info("Discovered {} notification listeners", classPathListeners.size());
    }

    private void updateListeners() {
        Collection<NotificationListener> updatedListeners = new ArrayList<>(classPathListeners);
        updatedListeners.addAll(registeredListeners);
        this.listeners = updatedListeners;
    }

    private void initializeListener(NotificationListener listener) {
        if (listener instanceof Initializable) {
            ((Initializable) listener).initialize();
        }
    }
}
