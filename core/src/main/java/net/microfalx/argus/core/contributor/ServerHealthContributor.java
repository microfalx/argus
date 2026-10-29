package net.microfalx.argus.core.contributor;

import net.microfalx.argus.api.Health;
import net.microfalx.argus.api.Resource;
import net.microfalx.argus.api.Thresholds;
import net.microfalx.argus.api.Unit;
import net.microfalx.jvm.ServerMetrics;
import net.microfalx.lang.JvmUtils;
import net.microfalx.lang.NumberUtils;
import net.microfalx.lang.StringUtils;
import net.microfalx.lang.annotation.Order;
import net.microfalx.lang.annotation.Provider;
import net.microfalx.lang.annotation.Tag;
import net.microfalx.metrics.Batch;
import net.microfalx.metrics.Metric;

import java.io.IOException;
import java.nio.file.FileStore;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.Pattern;

@SuppressWarnings("FieldMayBeFinal")
@Provider
@Order(20)
@Tag("server")
public class ServerHealthContributor extends AbstractHealthContributor {

    private final static String CPU_GROUP = "CPU";
    private final static String FILE_SYSTEM_GROUP = "File System";

    @Override
    public String getName() {
        return "Server";
    }

    @Override
    public boolean supports(Resource.Type type) {
        return type == Resource.Type.SERVER;
    }

    @Override
    public void update(Batch batch) {
        Health health = getHealth(Resource.Type.SERVER);
        batch.add(SCORE_METRIC, health.getScore());
    }

    @Override
    public void update(Health health) {
        ServerMetrics metrics = ServerMetrics.get();
        updateCpu(health, metrics);
        updateFileSystem(health, metrics);
    }

    public void updateCpu(Health health, ServerMetrics metrics) {
        health.update(asPercentageItem(CPU_TOTAL, metrics.getAverageTotalCpu()));
        health.update(asPercentageItem(CPU_USER, metrics.getAverageUserCpu()));
        health.update(asPercentageItem(CPU_SYSTEM, metrics.getAverageSystemCpu()));
        health.update(asPercentageItem(CPU_NICE, metrics.getAverageNiceCpu()));
        health.update(asPercentageItem(CPU_IO_WAIT, metrics.getAverageIoWaitCpu()));
    }

    public void updateFileSystem(Health health, ServerMetrics metrics) {
        Iterable<FileStore> fileStores = FileSystems.getDefault().getFileStores();
        for (FileStore fileStore : fileStores) {
            if (!isPhysical(fileStore)) continue;
            float usedPercent = getUsedPercent(fileStore);
            if (usedPercent < 0) continue;
            health.update(asPercentageItem(FILE_SYSTEM, usedPercent).withName(fileStore.name()));
        }
    }

    private boolean isPhysical(FileStore fileStore) {
        try {
            if (fileStore.getTotalSpace() <= 0) return false;
        } catch (IOException e) {
            return false;
        }

        String type = normalize(fileStore.type());
        String name = normalize(fileStore.name());

        if (JvmUtils.isWindows()) {
            return WINDOWS_LOCAL_TYPES.contains(type);
        } else {
            // unless Windows, Linux like OS
            if (LINUX_PHYSICAL_KEYS.contains(type + "|" + name)) return true;
            return name.startsWith("/dev/") || name.startsWith("uuid=")
                    || !LINUX_VIRTUAL_TYPES.contains(type);
        }
    }

    private float getUsedPercent(FileStore fileStore) {
        try {
            return NumberUtils.percent(fileStore.getTotalSpace() - fileStore.getUsableSpace(), fileStore.getTotalSpace());
        } catch (IOException e) {
            return -1f;
        }
    }

    private static String normalize(String value) {
        return StringUtils.emptyIfNull(value).trim().toLowerCase(Locale.ROOT);
    }

    private static Set<String> loadLinuxPhysicalKeys() {
        Set<String> keys = new HashSet<>();
        if (!JvmUtils.isLinux()) return keys;
        Path mountInfoPath = Path.of("/proc/self/mountinfo");
        if (!Files.isReadable(mountInfoPath)) return keys;
        try {
            for (String line : Files.readAllLines(mountInfoPath)) {
                String[] parts = line.split(" - ", 2);
                if (parts.length != 2) continue;
                String[] right = parts[1].split("\\s+");
                if (right.length < 2) continue;
                String type = normalize(right[0]);
                String source = normalize(right[1]);
                if (LINUX_VIRTUAL_TYPES.contains(type)) continue;
                if (source.startsWith("/dev/") || source.startsWith("uuid=")) {
                    keys.add(type + "|" + source);
                }
            }
        } catch (Exception ignored) {
            // Fallback to file store name heuristics when mountinfo cannot be read.
        }
        return keys;
    }

    @Override
    public Collection<Thresholds> getThresholds() {
        return List.of(CPU_TOTAL, CPU_USER, CPU_SYSTEM, CPU_NICE, CPU_IO_WAIT);
    }

    private static volatile Thresholds CPU_TOTAL = Thresholds.create("Total", 85f, 95f, Unit.PERCENT).withId("server.cpu.total").withGroup(CPU_GROUP);
    private static volatile Thresholds CPU_USER = Thresholds.create("User", 75f, 90f, Unit.PERCENT).withId("server.cpu.user").withGroup(CPU_GROUP);
    private static volatile Thresholds CPU_SYSTEM = Thresholds.create("System", 5f, 15f, Unit.PERCENT).withId("server.cpu.system").withGroup(CPU_GROUP);
    private static volatile Thresholds CPU_NICE = Thresholds.create("Nice", 85f, 95f, Unit.PERCENT).withId("server.cpu.total").withGroup(CPU_GROUP);
    private static volatile Thresholds CPU_IO_WAIT = Thresholds.create("I/O Wait", 5f, 15f, Unit.PERCENT).withId("server.cpu.total").withGroup(CPU_GROUP);

    private static volatile Thresholds FILE_SYSTEM = Thresholds.create("File System", 80f, 90f, Unit.PERCENT).withId("server.file.system").withGroup(FILE_SYSTEM_GROUP);

    private static final Pattern WINDOWS_DRIVE_PATTERN = Pattern.compile("^[A-Za-z]:\\\\");
    private static final Set<String> WINDOWS_LOCAL_TYPES = Set.of("ntfs", "fat", "fat32", "exfat", "refs");
    private static final Set<String> LINUX_VIRTUAL_TYPES = Set.of(
            "autofs", "bpf", "cgroup", "cgroup2", "configfs", "debugfs", "devpts", "devtmpfs", "fusectl",
            "hugetlbfs", "mqueue", "nsfs", "overlay", "proc", "procfs", "pstore", "ramfs", "securityfs",
            "squashfs", "sysfs", "tmpfs", "tracefs"
    );
    private static final Set<String> LINUX_PHYSICAL_KEYS = loadLinuxPhysicalKeys();

    public static final Metric SCORE_METRIC = Metric.get(ServerMetrics.METRIC_PREFIX + "score").withGroup("Health").withDisplayName("Server Score");
}
