package net.microfalx.argus.logger;

import net.microfalx.argus.api.LoggerService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class LoggerServiceImplTest {

    private LoggerServiceImpl loggerService;

    @BeforeEach
    void setup() {
        loggerService = new LoggerServiceImpl();
        loggerService.initialize();
    }

    @Test
    void serviceLocator() {
        assertNotNull(LoggerService.getInstance());
    }

    @Test
    void initialize() {
        assertNotNull(loggerService.getAlerts());
        assertNotNull(loggerService.getListeners());
    }

    @Test
    void register() {
        loggerService.register();
        assertNotNull(loggerService.getAlerts());
        assertNotNull(loggerService.getListeners());
    }

    @Test
    void getAppenders() {
        loggerService.register();
        assertEquals(2, loggerService.getAppenders().size());

    }

}