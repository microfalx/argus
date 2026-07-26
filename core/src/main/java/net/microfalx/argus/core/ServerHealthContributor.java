package net.microfalx.argus.core;

import net.microfalx.argus.api.Health;
import net.microfalx.argus.api.Thresholds;
import net.microfalx.argus.api.Unit;
import net.microfalx.jvm.ServerMetrics;
import net.microfalx.lang.annotation.Provider;

import java.util.Collection;
import java.util.List;

@SuppressWarnings("FieldMayBeFinal")
@Provider
public class ServerHealthContributor extends AbstractHealthContributor {

    private final static String CPU_GROUP = "CPU";

    @Override
    public void update(Health health) {
        ServerMetrics metrics = ServerMetrics.get();
        update(health, metrics);
    }

    public void update(Health health, ServerMetrics metrics) {
        health.update(asPercentageItem(CPU_TOTAL, metrics.getAverageTotalCpu()));
        health.update(asPercentageItem(CPU_USER, metrics.getAverageUserCpu()));
        health.update(asPercentageItem(CPU_SYSTEM, metrics.getAverageSystemCpu()));
        health.update(asPercentageItem(CPU_NICE, metrics.getAverageNiceCpu()));
        health.update(asPercentageItem(CPU_IO_WAIT, metrics.getAverageIoWaitCpu()));
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
}
