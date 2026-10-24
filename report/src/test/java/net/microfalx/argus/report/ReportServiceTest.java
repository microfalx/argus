package net.microfalx.argus.report;

import net.microfalx.argus.report.fragment.LoggerProvider;
import net.microfalx.lang.annotation.Provider;
import net.microfalx.resource.Resource;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.time.Duration;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class ReportServiceTest extends AbstractReportServiceTestCase {

    @Test
    void initialize() {
        assertEquals(6, reportService.getProviders().size());
    }

    @Test
    void createTemplate() throws IOException {
        LoggerProvider provider = new LoggerProvider();
        Template template = reportService.createTemplate("logger");
        assertNotNull(template);
        template.addVariable("report", new Report(reportService));
        provider.update(template);
        Resource resource = Resource.memory();
        template.render(resource);
        Assertions.assertThat(resource.loadAsString()).contains("This system");
    }

    @Test
    void sendReport() throws IOException {
        reportService.send(Duration.ofHours(1));
    }

    @Test
    void createReport() throws IOException {
        Report report = reportService.createReport();
        assertNotNull(report);
        assertEquals(6, report.getFragments().size());
    }

    @Provider
    public static class TestReportingListener implements ReportingListener {

        @Override
        public void update(Template template) {
            template.addVariable("test", "test");
        }

        @Override
        public boolean send(Set<String> destinations, String title,
                            Resource summary, Resource fullReport,
                            Optional<Resource> attachment) {
            ReportTestUtils.openInBrowser(summary);
            ReportTestUtils.openInBrowser(fullReport);
            return false;
        }
    }


}