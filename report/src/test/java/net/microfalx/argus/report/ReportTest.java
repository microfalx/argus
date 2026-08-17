package net.microfalx.argus.report;

import net.microfalx.resource.Resource;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;

class ReportTest extends AbstractReportServiceTestCase {

    @Test
    void defaultSettings() throws IOException {
        Report report = reportService.createReport().setTheme(Report.Theme.LIGHT);
        Resource resource = Resource.memory();
        report.render(resource);
        Assertions.assertThat(resource.loadAsString()).contains("btn-back-to-top")
                .contains("class=\"page\"").contains("Average CPU");
        openInBrowser(resource);
    }

    @Test
    void offlineSettings() throws IOException {
        Report report = reportService.createReport().setOffline(false).setTheme(Report.Theme.DARK);
        Resource resource = Resource.memory();
        report.render(resource);
        Assertions.assertThat(resource.loadAsString()).contains("btn-back-to-top")
                .contains("class=\"page\"").contains("Average CPU");
        openInBrowser(resource);
    }

}