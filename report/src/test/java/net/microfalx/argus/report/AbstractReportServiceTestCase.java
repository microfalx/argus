package net.microfalx.argus.report;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public abstract class AbstractReportServiceTestCase {


    @InjectMocks protected ReportService reportService;

    @BeforeEach
    void setup() throws Exception {
        ReportTestUtils.setupLogs();
        reportService.initialize();
        reportService.setSettings(new ReportSettings().withSystemName("Test System"));
        ReportTestUtils.collectStats(reportService);
    }


}
