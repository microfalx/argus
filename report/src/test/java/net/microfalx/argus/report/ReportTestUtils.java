package net.microfalx.argus.report;

import net.datafaker.Faker;
import net.microfalx.argus.api.HealthService;
import net.microfalx.argus.api.Issue;
import net.microfalx.argus.api.LoggerService;
import net.microfalx.jvm.ServerMetrics;
import net.microfalx.jvm.VirtualMachineMetrics;
import net.microfalx.lang.JvmUtils;
import net.microfalx.metrics.Metrics;
import net.microfalx.resource.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.*;
import java.io.File;

import static net.microfalx.lang.FileUtils.getFileName;
import static net.microfalx.lang.FileUtils.validateDirectoryExists;

public class ReportTestUtils {

    private static final Metrics METRICS = Metrics.of("Test");
    private static final Faker faker = new Faker();

    static void setupLogs() {
        File directory = new File(JvmUtils.getMavenTargetDirectory().orElseThrow(), "logs");
        JvmUtils.setLogsDirectory(validateDirectoryExists(directory));
    }

    static void openInBrowser(Resource resource) {
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

    static void collectStats(ReportService reportService) {
        LoggerService.getInstance().register();
        for (int i = 0; i < 5; i++) {
            scrape(reportService, true);
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    static void scrape(ReportService reportService, boolean force) {
        HealthService healthService = HealthService.getInstance();
        VirtualMachineMetrics vmm = VirtualMachineMetrics.get();
        ServerMetrics sm = ServerMetrics.get();
        if (force) {
            vmm.scrape();
            sm.scrape();
            healthService.update();
        }
        loggerActivity();
        metricsActivity();
        issueTypeActivities(reportService);
    }

    private static void issueTypeActivities(ReportService reportService) {
        reportService.register(Issue.create(Issue.Type.CONNECTIVITY, faker.internet().domainName()).withSeverity(getRandomSeverity()).withModule("Database").withDescription("Database is not reachable"));
        reportService.register(Issue.create(Issue.Type.STABILITY, faker.internet().domainName()).withSeverity(getRandomSeverity()).withModule("Database").withDescription("Maximum connections reached"));
        reportService.register(Issue.create(Issue.Type.DOS, faker.internet().domainName()).withModule("Registration").withDescription("Denial of service attack detected"));
        reportService.register(Issue.create(Issue.Type.DATA_INTEGRITY, getFileName(faker.file().fileName())).withModule("Parser").withDescription("Failed to parse the file, invalid format"));
    }

    private static Issue.Severity getRandomSeverity() {
        double random = Math.random();
        if (random < 0.3) return Issue.Severity.LOW;
        if (random < 0.6) return Issue.Severity.MEDIUM;
        if (random < 0.8) return Issue.Severity.HIGH;
        return Issue.Severity.CRITICAL;
    }

    private static void metricsActivity() {
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

    private static void loggerActivity() {
        Logger logger = LoggerFactory.getLogger(ReportTest.class);
        logger.error(faker.famousLastWords().lastWords(), new RuntimeException(faker.lorem().characters()));
        logger.warn(faker.chuckNorris().fact());
        logger.error(faker.lebowski().quote(), new RuntimeException(faker.simpsons().quote()));
    }
}
