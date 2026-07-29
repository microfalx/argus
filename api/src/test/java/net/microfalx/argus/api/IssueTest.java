package net.microfalx.argus.api;

import org.junit.jupiter.api.Test;

class IssueTest {

    @Test
    void issues() {
        Issue.create(Issue.Type.CONNECTIVITY, "Database", "Database is not reachable")
                .register();
    }
}