package net.microfalx.argus.core;

import lombok.extern.slf4j.Slf4j;
import net.microfalx.argus.api.HealthService;
import net.microfalx.argus.api.HealthSettings;
import net.microfalx.jvm.VirtualMachineMetrics;
import net.microfalx.jvm.model.VirtualMachine;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static net.microfalx.lang.FormatterUtils.formatBytes;
import static net.microfalx.lang.FormatterUtils.formatPercent;
import static net.microfalx.lang.ThreadUtils.sleepSeconds;

@Slf4j
class HealthServiceImplIntegrationTest {

    @Test
    void collectEvery10seconds() {
        HealthService healthService = HealthService.getInstance();
        updateScrapeInteger(10);
        while (true) {
            printStats();
            sleepSeconds(10);
        }
    }

    private void updateScrapeInteger(int interval) {
        HealthService healthService = HealthService.getInstance();
        healthService.setSettings(new HealthSettings().withScrapeInterval(Duration.ofSeconds(interval)));
    }

    private void printStats() {
        VirtualMachineMetrics jvmMetrics = VirtualMachineMetrics.get();
        VirtualMachine jvm = VirtualMachine.get();
        LOGGER.info("CPU={}/{}, Memory: Heap={}/{} -> Tenured={}/{}",
                formatPercent(jvmMetrics.getAverageCpuSinceStartup()),
                formatPercent(jvm.getProcess().getCpuTotal()),
                formatBytes(jvmMetrics.getHeapMemoryAverageSinceStartup()),
                formatBytes(jvm.getHeapUsedMemory()),
                formatBytes(jvmMetrics.getTenuredMemoryAverageSinceStartup()),
                formatBytes(jvm.getTenuredMemoryPool().getUsed()));
    }

}