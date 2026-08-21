package net.microfalx.argus.core.contributor;

import net.microfalx.argus.api.Health;
import net.microfalx.argus.api.Resource;
import net.microfalx.argus.api.Thresholds;
import net.microfalx.argus.api.Unit;
import net.microfalx.jvm.VirtualMachineMetrics;
import net.microfalx.jvm.model.BufferPool;
import net.microfalx.jvm.model.MemoryPool;
import net.microfalx.jvm.model.VirtualMachine;
import net.microfalx.lang.Initializable;
import net.microfalx.lang.JvmUtils;
import net.microfalx.lang.annotation.Provider;
import net.microfalx.lang.annotation.Tag;
import net.microfalx.metrics.Batch;
import net.microfalx.metrics.Metric;
import net.microfalx.metrics.statistics.MutableStatisticalSummary;
import net.microfalx.metrics.statistics.TimeWindowStatisticalSummary;

import java.time.Duration;
import java.util.Collection;
import java.util.List;

@SuppressWarnings("FieldMayBeFinal")
@Provider
@Tag("jvm")
public class VirtualMachineHealthContributor extends AbstractHealthContributor implements Initializable {

    private final static String JVM_GROUP = "JVM";
    private final static String MEMORY_GROUP = "Memory";
    private final static String GC_GROUP = "GC";
    private final static String FILE_SYSTEM_GROUP = "File System";
    private final static String OTHER_GROUP = "Other";

    private final Duration longAverage = Duration.ofHours(1);

    private final MutableStatisticalSummary threadsSummary = new TimeWindowStatisticalSummary(longAverage);
    private final MutableStatisticalSummary fileDescriptorsSummary = new TimeWindowStatisticalSummary(longAverage);

    @Override
    public String getName() {
        return "JVM";
    }

    @SuppressWarnings("NonAtomicOperationOnVolatileField")
    @Override
    public void initialize(Object... context) {

        fileDescriptorsSummary.add(1000);
        threadsSummary.add(100);

        OTHER_FILE_DESCRIPTORS = OTHER_FILE_DESCRIPTORS.with((float) fileDescriptorsSummary.getMean() * 2,
                (float) fileDescriptorsSummary.getMean() * 3);
        OTHER_THREADS = OTHER_THREADS.with((float) threadsSummary.getMean() * 2,
                (float) threadsSummary.getMean() * 3);
    }

    @Override
    public boolean supports(Resource.Type type) {
        return type == Resource.Type.SERVICE;
    }

    @Override
    public void update(Health health) {
        VirtualMachineMetrics virtualMachineMetrics = VirtualMachineMetrics.get();
        VirtualMachine virtualMachine = virtualMachineMetrics.getLast();
        Health.Group jvmGroup = health.getGroup(JVM_GROUP);
        updateMemory(jvmGroup, virtualMachineMetrics, virtualMachine);
        updateGc(jvmGroup, virtualMachineMetrics);
        updateFileSystem(jvmGroup);
        updateOther(jvmGroup, virtualMachineMetrics, virtualMachine);
    }

    @Override
    public void update(Batch batch) {
        Health health = getHealth(Resource.Type.SERVICE);
        batch.add(SCORE_METRIC, health.getScore());
    }

    @Override
    public Collection<Thresholds> getThresholds() {
        return List.of(MEMORY_TENURED, MEMORY_EDEN, MEMORY_METASPACE, MEMORY_BUFFERS,
                FILE_SYSTEM_HOME, FILE_SYSTEM_VARIABLE, FILE_SYSTEM_TEMPORARY,
                OTHER_THREADS, OTHER_FILE_DESCRIPTORS);
    }

    private void updateMemory(Health.Group group, VirtualMachineMetrics metrics, VirtualMachine virtualMachine) {
        group = group.getGroup(MEMORY_GROUP);
        group.update(asPercentageItem(MEMORY_EDEN, metrics.getAverageEdenMemory(), virtualMachine.getEdenMemoryPool()));
        group.update(asPercentageItem(MEMORY_TENURED, metrics.getAverageTenuredMemory(), virtualMachine.getTenuredMemoryPool()));
        group.update(asPercentageItem(MEMORY_METASPACE, metrics.getAverageMetaspaceMemory(), virtualMachine.getMetapaceMemoryPool()));
        group.update(asPercentageItem(MEMORY_BUFFERS, virtualMachine.getBufferPools(BufferPool.Type.DIRECT), "Direct"));
        group.update(asPercentageItem(MEMORY_BUFFERS, virtualMachine.getBufferPools(BufferPool.Type.MAPPED), "Mapped"));
    }

    private void updateFileSystem(Health.Group group) {
        group = group.getGroup(FILE_SYSTEM_GROUP);
        group.update(asPercentageItem(FILE_SYSTEM_HOME, JvmUtils.getHomeDirectory()));
        group.update(asPercentageItem(FILE_SYSTEM_VARIABLE, JvmUtils.getVariableDirectory()));
        group.update(asPercentageItem(FILE_SYSTEM_TEMPORARY, JvmUtils.getTemporaryDirectory()));
    }

    private void updateOther(Health.Group group, VirtualMachineMetrics metrics, VirtualMachine virtualMachine) {
        group = group.getGroup(OTHER_GROUP);
        synchronized (lock) {
            int threads = virtualMachine.getProcess().getThreads();
            threadsSummary.add(threads);
            group.update(asCounterItem(OTHER_THREADS, metrics.getAverageThreads()));

            int fileDescriptors = virtualMachine.getProcess().getFileDescriptors();
            fileDescriptorsSummary.add(fileDescriptors);
            group.update(asCounterItem(OTHER_FILE_DESCRIPTORS, fileDescriptors));
        }
    }

    private void updateGc(Health.Group group, VirtualMachineMetrics metrics) {
        group = group.getGroup(GC_GROUP);
        group.update(asDurationItem(GC_EDEN, metrics.getAverageGcEdenDuration()));
        group.update(asDurationItem(GC_TENURED, metrics.getAverageGcTenuredDuration()));
    }

    private Health.Item asPercentageItem(Thresholds thresholds, long used, MemoryPool memoryPool) {
        return Health.Item.create(thresholds,
                used, memoryPool.getMaximum(), Unit.BYTE);
    }

    private Health.Item asPercentageItem(Thresholds thresholds, BufferPool bufferPool, String suffix) {
        return Health.Item.create(thresholds,
                        bufferPool.getUsed(), bufferPool.getMaximum(), Unit.BYTE)
                .withName(thresholds.getName() + " " + suffix);
    }


    private static volatile Thresholds MEMORY_TENURED = Thresholds.create("Tenured", 85f, 95f, Unit.PERCENT).withId("jvm.memory.tenured").withGroup(MEMORY_GROUP);
    private static volatile Thresholds MEMORY_EDEN = Thresholds.create("Eden", 85f, 95f, Unit.PERCENT).withId("jvm.memory.eden").withGroup(MEMORY_GROUP);
    private static volatile Thresholds MEMORY_METASPACE = Thresholds.create("Metaspace", 100f, 100f, Unit.PERCENT).withId("jvm.memory.metaspace").withGroup(MEMORY_GROUP);
    private static volatile Thresholds MEMORY_BUFFERS = Thresholds.create("Buffers", 85f, 95f, Unit.PERCENT).withId("jvm.memory.buffers").withGroup(MEMORY_GROUP);

    private static volatile Thresholds GC_EDEN = Thresholds.create("Eden", 50, 100, Unit.MILLISECOND).withId("jvm.gc.eden").withGroup(GC_GROUP);
    private static volatile Thresholds GC_TENURED = Thresholds.create("Tenured", 100, 200, Unit.MILLISECOND).withId("jvm.gc.tenured").withGroup(GC_GROUP);

    private static volatile Thresholds FILE_SYSTEM_HOME = Thresholds.create("Home Directory", 85f, 95f, Unit.PERCENT).withId("jvm.filesystem.home").withGroup(FILE_SYSTEM_GROUP);
    private static volatile Thresholds FILE_SYSTEM_VARIABLE = Thresholds.create("Variable Directory", 85f, 95f, Unit.PERCENT).withId("jvm.filesystem.variable").withGroup(FILE_SYSTEM_GROUP);
    private static volatile Thresholds FILE_SYSTEM_TEMPORARY = Thresholds.create("Temporary Directory", 85f, 95f, Unit.PERCENT).withId("jvm.filesystem.temporary").withGroup(FILE_SYSTEM_GROUP);

    private static volatile Thresholds OTHER_THREADS = Thresholds.create("Threads", 100f, 200f, Unit.COUNTER).withId("jvm.threads").withGroup(OTHER_GROUP);
    private static volatile Thresholds OTHER_FILE_DESCRIPTORS = Thresholds.create("File Descriptors", 500f, 1000f, Unit.COUNTER).withId("jvm.threads").withGroup(OTHER_GROUP);

    public static final Metric SCORE_METRIC = Metric.get(VirtualMachineMetrics.METRIC_PREFIX + "score").withGroup("Health").withDisplayName("JVM Score");
}
