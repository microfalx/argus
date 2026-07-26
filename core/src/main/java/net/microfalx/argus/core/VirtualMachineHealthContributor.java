package net.microfalx.argus.core;

import net.microfalx.argus.api.Health;
import net.microfalx.argus.api.Thresholds;
import net.microfalx.argus.api.Unit;
import net.microfalx.jvm.VirtualMachineMetrics;
import net.microfalx.jvm.model.BufferPool;
import net.microfalx.jvm.model.GarbageCollection;
import net.microfalx.jvm.model.MemoryPool;
import net.microfalx.jvm.model.VirtualMachine;
import net.microfalx.lang.JvmUtils;
import net.microfalx.lang.annotation.Provider;
import net.microfalx.metrics.statistics.MutableStatisticalSummary;
import net.microfalx.metrics.statistics.TimeWindowStatisticalSummary;

import java.time.Duration;
import java.util.Collection;
import java.util.List;

@SuppressWarnings("FieldMayBeFinal")
@Provider
public class VirtualMachineHealthContributor extends AbstractHealthContributor {

    private final static String JVM_GROUP = "JVM";
    private final static String MEMORY_GROUP = JVM_GROUP + " / Memory";
    private final static String GC_GROUP = JVM_GROUP + " / GC";
    private final static String FILE_SYSTEM_GROUP = JVM_GROUP + " / File System";
    private final static String OTHER_GROUP = JVM_GROUP + " / Other";

    private final Duration longAverage = Duration.ofHours(1);

    private final MutableStatisticalSummary threadsSummary = new TimeWindowStatisticalSummary(longAverage);
    private final MutableStatisticalSummary fileDescriptorsSummary = new TimeWindowStatisticalSummary(longAverage);

    @Override
    public void update(Health health) {
        VirtualMachineMetrics virtualMachineMetrics = VirtualMachineMetrics.get();
        VirtualMachine virtualMachine = virtualMachineMetrics.getLast();
        updateMemory(health, virtualMachineMetrics);
        updateGc(health, virtualMachineMetrics);
        updateFileSystem(health);
        updateOther(health, virtualMachineMetrics, virtualMachine);
    }

    @Override
    public Collection<Thresholds> getThresholds() {
        return List.of(MEMORY_TENURED, MEMORY_EDEN, MEMORY_METASPACE, MEMORY_BUFFERS,
                FILE_SYSTEM_HOME, FILE_SYSTEM_VARIABLE, FILE_SYSTEM_TEMPORARY,
                OTHER_THREADS, OTHER_FILE_DESCRIPTORS);
    }

    private void updateMemory(Health health, VirtualMachineMetrics metrics) {
        health.update(asPercentageItem(MEMORY_TENURED, metrics.getAverageTenuredMemory()));
        health.update(asPercentageItem(MEMORY_EDEN, metrics.getAverageEdenMemory()));
        health.update(asPercentageItem(MEMORY_METASPACE, metrics.getAverageMetaspaceMemory()));
        //health.update(asPercentageItem(MEMORY_BUFFERS, virtualMachine.getBufferPools(BufferPool.Type.DIRECT), "Direct"));
        // health.update(asPercentageItem(MEMORY_BUFFERS, virtualMachine.getBufferPools(BufferPool.Type.MAPPED), "Mapped"));
    }

    private void updateFileSystem(Health health) {
        health.update(asPercentageItem(FILE_SYSTEM_HOME, JvmUtils.getHomeDirectory()));
        health.update(asPercentageItem(FILE_SYSTEM_VARIABLE, JvmUtils.getVariableDirectory()));
        health.update(asPercentageItem(FILE_SYSTEM_TEMPORARY, JvmUtils.getTemporaryDirectory()));
    }

    private void updateOther(Health health, VirtualMachineMetrics metrics, VirtualMachine virtualMachine) {
        int threads = virtualMachine.getProcess().getThreads();
        threadsSummary.add(threads);
        health.update(OTHER_GROUP, asCounterItem(OTHER_THREADS, metrics.getAverageThreads()));

        int fileDescriptors = virtualMachine.getProcess().getFileDescriptors();
        fileDescriptorsSummary.add(fileDescriptors);
        health.update(OTHER_GROUP, asCounterItem(OTHER_FILE_DESCRIPTORS, fileDescriptors));
    }

    private void updateGc(Health health, VirtualMachineMetrics metrics) {
        health.update(OTHER_GROUP, asDurationItem(GC_EDEN, metrics.getAverageGcEdenDuration()));
        health.update(OTHER_GROUP, asDurationItem(GC_TENURED, metrics.getAverageGcTenuredDuration()));
    }

    private Health.Item asBytesItem(Thresholds thresholds, MemoryPool memoryPool) {
        return Health.Item.create(thresholds,
                memoryPool.getUsed(), memoryPool.getMaximum(), Unit.BYTE);
    }

    private Health.Item asPercentageItem(Thresholds thresholds, BufferPool bufferPool, String suffix) {
        return Health.Item.create(thresholds,
                        bufferPool.getUsed(), bufferPool.getMaximum(), Unit.BYTE)
                .withName(thresholds.getName() + " " + suffix);
    }


    private static volatile Thresholds MEMORY_TENURED = Thresholds.create("Tenured", 85f, 95f, Unit.PERCENT).withId("jvm.memory.tenured").withGroup(MEMORY_GROUP);
    private static volatile Thresholds MEMORY_EDEN = Thresholds.create("Eden", 85f, 95f, Unit.PERCENT).withId("jvm.memory.eden").withGroup(MEMORY_GROUP);
    private static volatile Thresholds MEMORY_METASPACE = Thresholds.create("Metaspace", 85f, 95f, Unit.PERCENT).withId("jvm.memory.metaspace").withGroup(MEMORY_GROUP);
    private static volatile Thresholds MEMORY_BUFFERS = Thresholds.create("Buffers", 85f, 95f, Unit.PERCENT).withId("jvm.memory.buffers").withGroup(MEMORY_GROUP);

    private static volatile Thresholds GC_EDEN = Thresholds.create("Eden", 50, 100, Unit.MILLISECOND).withId("jvm.gc.eden").withGroup(JVM_GROUP);
    private static volatile Thresholds GC_TENURED = Thresholds.create("Tenured", 100, 200, Unit.MILLISECOND).withId("jvm.gc.tenured").withGroup(JVM_GROUP);

    private static volatile Thresholds FILE_SYSTEM_HOME = Thresholds.create("Home Directory", 85f, 95f, Unit.PERCENT).withId("jvm.filesystem.home").withGroup(FILE_SYSTEM_GROUP);
    private static volatile Thresholds FILE_SYSTEM_VARIABLE = Thresholds.create("Variable Directory", 85f, 95f, Unit.PERCENT).withId("jvm.filesystem.variable").withGroup(FILE_SYSTEM_GROUP);
    private static volatile Thresholds FILE_SYSTEM_TEMPORARY = Thresholds.create("Temporary Directory", 85f, 95f, Unit.PERCENT).withId("jvm.filesystem.temporary").withGroup(FILE_SYSTEM_GROUP);

    private static volatile Thresholds OTHER_THREADS = Thresholds.create("Threads", 85f, 95f, Unit.PERCENT).withId("jvm.threads").withGroup(OTHER_GROUP);
    private static volatile Thresholds OTHER_FILE_DESCRIPTORS = Thresholds.create("File Descriptors", 85f, 95f, Unit.PERCENT).withId("jvm.threads").withGroup(OTHER_GROUP);
}
