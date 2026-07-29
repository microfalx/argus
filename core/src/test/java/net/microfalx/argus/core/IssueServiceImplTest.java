package net.microfalx.argus.core;

import net.microfalx.argus.api.Issue;
import net.microfalx.argus.api.IssueService;
import net.microfalx.lang.service.ServiceFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class IssueServiceImplTest {

    private IssueService issueService;

    @BeforeEach
    void setup() {
        ServiceFactory.shutdown(IssueService.class);
        issueService = IssueService.getInstance();
    }

    @Test
    void getInstance() {
        IssueService instance = IssueService.getInstance();
        assertNotNull(instance);
    }


    @Test
    void issues() {
        Issue.create(Issue.Type.CONNECTIVITY, "Database", "Database is not reachable")
                .register();
        assertEquals(1, issueService.getPendingIssues().size());
    }

}