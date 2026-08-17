package net.microfalx.argus.report;

import net.microfalx.argus.api.HealthService;
import net.microfalx.argus.api.Issue;
import net.microfalx.argus.api.LoggerService;
import net.microfalx.jvm.ServerMetrics;
import net.microfalx.jvm.VirtualMachineMetrics;
import net.microfalx.lang.FileUtils;
import net.microfalx.lang.JvmUtils;
import net.microfalx.metrics.Counter;
import net.microfalx.metrics.Metrics;
import net.microfalx.metrics.Summary;
import net.microfalx.resource.Resource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.*;
import java.io.File;

import static net.microfalx.lang.FileUtils.validateDirectoryExists;

@ExtendWith(MockitoExtension.class)
public abstract class AbstractReportServiceTestCase {

    private static final Metrics METRICS = Metrics.of("Test");

    @InjectMocks protected ReportService reportService;

    @BeforeEach
    void setup() throws Exception {
        System.setProperty("logback.debug", "false");
        File directory = new File(JvmUtils.getMavenTargetDirectory().orElseThrow(), "logs");
        JvmUtils.setLogsDirectory(validateDirectoryExists(directory));
        reportService.initialize();
        reportService.setSettings(new ReportSettings().withSystemName("Test System"));
        collectVMStats();
    }

    void openInBrowser(Resource resource) {
        if (!Boolean.parseBoolean(System.getProperty("test.report.open", "false"))) {
            return;
        }
        File report = JvmUtils.getTemporaryFile("report", ".html");
        try {
            Resource.file(report).copyFrom(resource);
            if (!Desktop.isDesktopSupported()) {
                System.out.println("Desktop API is not supported on this platform.");
            } else {
                Desktop desktop = Desktop.getDesktop();
                desktop.open(report);
            }
        } catch (Exception e) {
            System.err.println("Failed to copy report to temporary file: " + e.getMessage());
        }
    }

    void collectVMStats() {
        LoggerService.getInstance().register();
        HealthService healthService = HealthService.getInstance();
        VirtualMachineMetrics vmm = VirtualMachineMetrics.get();
        ServerMetrics sm = ServerMetrics.get();
        for (int i = 0; i < 5; i++) {
            vmm.scrape();
            sm.scrape();
            healthService.update();
            loggerActivity();
            metricsActivity();
            issueTypeActivities();
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private void issueTypeActivities() {
        reportService.register(Issue.create(Issue.Type.CONNECTIVITY, "Database", "Database is not reachable"));
    }

    private void metricsActivity() {
        METRICS.increment("test.gauge");
        if (Math.random() > 0.5) METRICS.decrement("test.gauge");
        METRICS.count("test.counter");
        METRICS.time("test.timer", t -> {
            try {
                Thread.sleep((long) (Math.random() * 10));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
        METRICS.getSummary("test.summary").record(t -> {
            try {
                Thread.sleep((long) (Math.random() * 10));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
    }

    private void loggerActivity() {
        Logger logger = LoggerFactory.getLogger(ReportTest.class);
        logger.error("Something is wrong", new RuntimeException("Test exception"));
        logger.warn("Something is wrong as warn");
        logger.error("Something is wrong with exception", new RuntimeException("Test exception"));
    }
}
