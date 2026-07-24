package net.microfalx.argus.core;

import net.microfalx.argus.api.IssueService;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;

class IssueServiceImplTest {

    @Test
    void getInstance() {
        IssueService instance = IssueService.getInstance();
        assertNotNull(instance);
    }

}