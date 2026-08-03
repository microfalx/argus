package net.microfalx.argus.logger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;

class LoggerServiceImplTest {

    private LoggerServiceImpl loggerService;

    @BeforeEach
    void setup() {
        loggerService = new LoggerServiceImpl();
        loggerService.initialize();
    }

    @Test
    void initialize() {
        assertNotNull(loggerService.getAlerts());
        assertNotNull(loggerService.getListeners());
    }

}