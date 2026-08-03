package net.microfalx.argus.report;

import net.microfalx.argus.report.fragment.LoggerProvider;
import net.microfalx.resource.Resource;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class ReportServiceTest extends AbstractReportServiceTestCase {

    @Test
    void initialize() {
        assertEquals(4, reportService.getProviders().size());
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
    void createReport() throws IOException {
        Report report = reportService.createReport();
        assertNotNull(report);
        assertEquals(4, report.getFragments().size());
    }


}