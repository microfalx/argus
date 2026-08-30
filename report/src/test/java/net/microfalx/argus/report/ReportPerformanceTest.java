package net.microfalx.argus.report;

import lombok.extern.slf4j.Slf4j;
import net.microfalx.jvm.VirtualMachineMetrics;
import net.microfalx.jvm.model.VirtualMachine;
import net.microfalx.lang.JvmUtils;
import net.microfalx.resource.Resource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;

import static java.lang.System.currentTimeMillis;
import static net.microfalx.lang.ExceptionUtils.getRootCauseDescription;
import static net.microfalx.lang.FormatterUtils.formatBytes;
import static net.microfalx.lang.FormatterUtils.formatPercent;
import static net.microfalx.lang.ThreadUtils.sleepSeconds;
import static net.microfalx.lang.TimeUtils.*;

@Disabled
@Slf4j
public class ReportPerformanceTest {

    private long lastOpen = currentTimeMillis();
    private Resource report;
    private ReportService reportService;

    @BeforeEach
    void setup() {
        System.setProperty("test.report.open", "true");
        reportService = ReportService.getInstance();
    }

    @Test
    void collectEvery10seconds() {
        ReportTestUtils.collectStats(reportService);
        while (true) {
            ReportTestUtils.scrape(reportService, true);
            printStats();
            Report report = reportService.createReport().setTheme(Report.Theme.LIGHT);
            Resource resource = Resource.memory();
            try {
                report.render(resource);
            } catch (IOException e) {
                LOGGER.warn("Failed to render report ", e);
            }
            saveReportLocation(resource);
            checkIfOpen();
            sleepSeconds(10);
        }
    }

    private void printStats() {
        VirtualMachineMetrics jvmMetrics = VirtualMachineMetrics.get();
        VirtualMachine jvm = VirtualMachine.get();
        LOGGER.info("Generate report: CPU={}/{}, Memory: Heap={}/{} -> Tenured={}/{}",
                formatPercent(jvmMetrics.getAverageCpuSinceStartup()),
                formatPercent(jvm.getProcess().getCpuTotal()),
                formatBytes(jvmMetrics.getHeapMemoryAverageSinceStartup()),
                formatBytes(jvm.getHeapUsedMemory()),
                formatBytes(jvmMetrics.getTenuredMemoryAverageSinceStartup()),
                formatBytes(jvm.getTenuredMemoryPool().getUsed()));
    }

    private void saveReportLocation(Resource resource) {
        File file = new File(JvmUtils.getMavenTargetDirectory().orElseThrow(), "report.html");
        report = Resource.file(file);
        try {
            report.copyFrom(resource);
        } catch (IOException e) {
            LOGGER.warn("Failed to save report to {}, reason: {}", file, getRootCauseDescription(e));
        }
    }

    private void checkIfOpen() {
        if (millisSince(lastOpen) < ONE_MINUTE) return;
        lastOpen = currentTimeMillis();
        ReportTestUtils.openInBrowser(report);
    }
}
