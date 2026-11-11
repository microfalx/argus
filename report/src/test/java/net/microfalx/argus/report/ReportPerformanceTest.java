package net.microfalx.argus.report;

import lombok.extern.slf4j.Slf4j;
import net.microfalx.argus.diagnostics.Diagnostics;
import net.microfalx.argus.diagnostics.DiagnosticsService;
import net.microfalx.argus.diagnostics.FlightRecording;
import net.microfalx.argus.diagnostics.Profile;
import net.microfalx.jvm.VirtualMachineMetrics;
import net.microfalx.jvm.model.VirtualMachine;
import net.microfalx.lang.JvmUtils;
import net.microfalx.lang.TimeUtils;
import net.microfalx.resource.Resource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.time.Duration;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static java.lang.System.currentTimeMillis;
import static net.microfalx.lang.EnumUtils.toLabel;
import static net.microfalx.lang.ExceptionUtils.getRootCauseDescription;
import static net.microfalx.lang.FormatterUtils.formatBytes;
import static net.microfalx.lang.FormatterUtils.formatPercent;
import static net.microfalx.lang.ThreadUtils.sleepSeconds;
import static net.microfalx.lang.TimeUtils.*;

@Disabled
@Slf4j
public class ReportPerformanceTest {

    private long lastOpen = TimeUtils.oneHourAgo();
    private Resource report;
    private ReportService reportService;

    private static final double DIAGNOSTICS_PROBABILITY = 0.4;
    private static final Duration SHORT_DIAGNOSTICS = Duration.ofSeconds(5);
    private static final Diagnostics.Type[] DIAGNOSTICS_TYPES = {
            Diagnostics.Type.THREAD_DUMP, Diagnostics.Type.THREAD_DUMP,
            Diagnostics.Type.VM_REPORT, Diagnostics.Type.VM_REPORT,
            Diagnostics.Type.HISTOGRAM, Diagnostics.Type.HISTOGRAM,
            Diagnostics.Type.FLIGHT_RECORDING, Diagnostics.Type.PROFILE
    };

    private final Random random = ThreadLocalRandom.current();
    private final AtomicBoolean diagnosticsInProgress = new AtomicBoolean();
    private final AtomicInteger diagnosticsCounter = new AtomicInteger();
    private final ExecutorService diagnosticsExecutor = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "diagnostics-test");
        thread.setDaemon(true);
        return thread;
    });

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
            captureRandomDiagnostics();
            printStats();
            Report.Navigation navigation = new Report.Navigation(Report.Theme.DARK).withCollapsed(false);
            Report report = reportService.createReport().setTheme(Report.Theme.LIGHT)
                    .setNavigation(navigation);
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

    /**
     * Captures (asynchronously) a random diagnostics, so the report has something to display.
     * <p>
     * Mostly fast, text based diagnostics are captured; short flight recordings (binary content) and profiles
     * (which fail if the async profiler is not installed) are captured occasionally, to exercise all cases.
     */
    private void captureRandomDiagnostics() {
        if (diagnosticsInProgress.get() || random.nextDouble() > DIAGNOSTICS_PROBABILITY) return;
        Diagnostics.Type type = DIAGNOSTICS_TYPES[random.nextInt(DIAGNOSTICS_TYPES.length)];
        Diagnostics.Options options = switch (type) {
            case FLIGHT_RECORDING -> FlightRecording.Options.builder().duration(SHORT_DIAGNOSTICS).build();
            case PROFILE -> Profile.Options.builder().duration(SHORT_DIAGNOSTICS).build();
            default -> null;
        };
        String name = toLabel(type) + " #" + diagnosticsCounter.incrementAndGet();
        diagnosticsInProgress.set(true);
        diagnosticsExecutor.execute(() -> {
            try {
                LOGGER.info("Capture diagnostics '{}'", name);
                Diagnostics diagnostics = DiagnosticsService.getInstance().start(type, name, options);
                diagnostics.collect();
                LOGGER.info("Diagnostics '{}' captured in {}, failure: {}", name, diagnostics.getDuration(),
                        diagnostics.getFailure());
            } catch (Exception e) {
                LOGGER.warn("Failed to capture diagnostics '{}', reason: {}", name, getRootCauseDescription(e));
            } finally {
                diagnosticsInProgress.set(false);
            }
        });
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
