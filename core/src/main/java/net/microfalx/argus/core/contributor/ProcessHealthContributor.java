package net.microfalx.argus.core.contributor;

import net.microfalx.argus.api.Health;
import net.microfalx.argus.api.Resource;
import net.microfalx.argus.api.Thresholds;
import net.microfalx.argus.api.Unit;
import net.microfalx.jvm.VirtualMachineMetrics;
import net.microfalx.jvm.VirtualMachineUtils;
import net.microfalx.jvm.model.Process;
import net.microfalx.jvm.model.VirtualMachine;
import net.microfalx.lang.annotation.Order;
import net.microfalx.lang.annotation.Provider;
import net.microfalx.lang.annotation.Tag;
import net.microfalx.metrics.statistics.MutableStatisticalSummary;
import net.microfalx.metrics.statistics.TimeWindowStatisticalSummary;

import java.time.Duration;
import java.util.Collection;
import java.util.List;

/**
 * Tracks the CPU and memory used by the (JVM) process, relative to the limits available to the process.
 * <p>
 * The limits are provided by {@link VirtualMachineUtils#getMaximumCores()} and
 * {@link VirtualMachineUtils#getMaximumMemory()}, which take into account container (cgroup) limits and fall back
 * to the server limits.
 */
@SuppressWarnings("FieldMayBeFinal")
@Provider
@Order(5)
@Tag("process")
public class ProcessHealthContributor extends AbstractHealthContributor {

    private final static String PROCESS_GROUP = "Process";
    private final static String CPU_GROUP = "CPU";
    private final static String MEMORY_GROUP = "Memory";

    private final Duration averageInterval = Duration.ofMinutes(5);

    private final MutableStatisticalSummary cpuSummary = new TimeWindowStatisticalSummary(averageInterval);
    private final MutableStatisticalSummary memorySummary = new TimeWindowStatisticalSummary(averageInterval);

    @Override
    public String getName() {
        return "Process";
    }

    @Override
    public boolean supports(Resource.Type type) {
        return type == Resource.Type.SERVICE;
    }

    @Override
    public void update(Health health) {
        VirtualMachine virtualMachine = VirtualMachineMetrics.get().getLast();
        if (virtualMachine == null || virtualMachine.getProcess() == null) return;
        Process process = virtualMachine.getProcess();
        Health.Group group = health.getGroup(PROCESS_GROUP);
        updateCpu(group, process);
        updateMemory(group, process);
    }

    @Override
    public Collection<Thresholds> getThresholds() {
        return List.of(CPU_TOTAL, MEMORY_RESIDENT);
    }

    private void updateCpu(Health.Group group, Process process) {
        // the process CPU is reported relative to a single core, normalize to the cores available to the process
        int cores = Math.max(1, VirtualMachineUtils.getMaximumCores());
        float cpu = Math.min(100f, Math.max(0f, process.getCpuTotal() / cores));
        float averageCpu;
        synchronized (lock) {
            cpuSummary.add(cpu);
            averageCpu = (float) cpuSummary.getMean();
        }
        group.getGroup(CPU_GROUP).update(asPercentageItem(CPU_TOTAL, averageCpu));
    }

    private void updateMemory(Health.Group group, Process process) {
        long maximumMemory = VirtualMachineUtils.getMaximumMemory();
        if (maximumMemory <= 0) return;
        long averageMemory;
        synchronized (lock) {
            memorySummary.add(process.getMemoryResident());
            averageMemory = (long) memorySummary.getMean();
        }
        group.getGroup(MEMORY_GROUP).update(Health.Item.create(MEMORY_RESIDENT, averageMemory, maximumMemory, Unit.BYTE));
    }

    private static volatile Thresholds CPU_TOTAL = Thresholds.create("Total", 85f, 95f, Unit.PERCENT).withId("process.cpu.total").withGroup(CPU_GROUP);
    private static volatile Thresholds MEMORY_RESIDENT = Thresholds.create("Resident", 85f, 95f, Unit.PERCENT).withId("process.memory.resident").withGroup(MEMORY_GROUP);

}
