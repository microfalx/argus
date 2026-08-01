package net.microfalx.argus.core;

import net.microfalx.argus.api.Notification;
import net.microfalx.argus.api.NotificationListener;
import net.microfalx.argus.api.NotificationService;
import net.microfalx.lang.Initializable;
import net.microfalx.threadpool.ThreadPool;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collection;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class NotificationServiceImplTest {

    @Mock
    private ThreadPool threadPool;

    private NotificationServiceImpl notificationService;

    @BeforeEach
    void setup() {
        notificationService = new NotificationServiceImpl();
        notificationService.setThreadPool(threadPool);
        notificationService.initialize();
        notificationService.start();
    }

    @Test
    void getInstance() {
        NotificationService instance = NotificationService.getInstance();
        assertNotNull(instance);
    }

    @Test
    void getListeners() {
        Collection<NotificationListener> listeners = notificationService.getListeners();
        assertNotNull(listeners);

        assertThrows(UnsupportedOperationException.class,
                () -> listeners.add(mock(NotificationListener.class)));
    }

    @Test
    void register() {
        int initialSize = notificationService.getListeners().size();
        InitializableListener listener = new InitializableListener();

        notificationService.register(listener);

        assertEquals(initialSize + 1, notificationService.getListeners().size());
        assertTrue(notificationService.getListeners().contains(listener));
        assertEquals(1, listener.initializeCount.get());

        notificationService.unregister(listener);
        assertEquals(initialSize, notificationService.getListeners().size());
        assertFalse(notificationService.getListeners().contains(listener));
    }

    @Test
    void send() {
        NotificationListener first = mock(NotificationListener.class);
        NotificationListener failing = mock(NotificationListener.class);
        NotificationListener third = mock(NotificationListener.class);
        Notification notification = new Notification();

        doThrow(new RuntimeException("Failure")).when(failing).onNotification(notification);

        notificationService.register(first);
        notificationService.register(failing);
        notificationService.register(third);

        notificationService.send(notification);

        verify(first, times(1)).onNotification(notification);
        verify(failing, times(1)).onNotification(notification);
        verify(third, times(1)).onNotification(notification);
    }

    @Test
    void initialize() {
        InitializableListener listener = new InitializableListener();
        notificationService.register(listener);

        assertTrue(notificationService.getListeners().contains(listener));
        assertEquals(1, listener.initializeCount.get());

        notificationService.initialize();

        assertTrue(notificationService.getListeners().contains(listener));
        assertEquals(1, listener.initializeCount.get());
    }

    private static final class InitializableListener implements NotificationListener, Initializable {

        private final AtomicInteger initializeCount = new AtomicInteger();

        @Override
        public void onNotification(Notification notification) {
        }

        @Override
        public void initialize(Object... context) {
            initializeCount.incrementAndGet();
        }
    }
}