package net.microfalx.argus.core;

import net.microfalx.argus.api.HealthService;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;

class HealthServiceImplTest {

    @Test
    void getInstance() {
        HealthService instance = HealthService.getInstance();
        assertNotNull(instance);
    }

}