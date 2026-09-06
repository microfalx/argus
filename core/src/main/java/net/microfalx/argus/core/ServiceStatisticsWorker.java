package net.microfalx.argus.core;

import net.microfalx.argus.api.HealthService;
import net.microfalx.jvm.ObjectSize;
import net.microfalx.jvm.VirtualMachineMetrics;
import net.microfalx.lang.service.Service;
import net.microfalx.lang.service.ServiceLocator;
import net.microfalx.metrics.statistics.MutableStatisticalSummary;

public class ServiceStatisticsWorker implements Runnable {

    private HealthService healthService;

    @Override
    public void run() {
        ServiceLocator.getServices().forEach(this::updateStatistics);
    }

    private void updateStatistics(Service service) {
        updateMemoryUsage(service);
    }

    private void updateMemoryUsage(Service service) {
        Object realService = ServiceLocator.getRealService(service);
        ObjectSize memoryUsage = VirtualMachineMetrics.get().getDeepSize(realService);
        MutableStatisticalSummary trend = (MutableStatisticalSummary) getHealthService().getTrend(service, Service.Metric.MEMORY_USAGE);
        trend.add(memoryUsage.getSizeOf());
        ServiceLocator.report(service, Service.Metric.MEMORY_USAGE, memoryUsage.getSizeOf());

        trend = (MutableStatisticalSummary) getHealthService().getTrend(service, Service.Metric.MEMORY_OBJECTS);
        trend.add(memoryUsage.getCountOf());
        ServiceLocator.report(service, Service.Metric.MEMORY_OBJECTS, memoryUsage.getCountOf());
    }

    private HealthService getHealthService() {
        if (healthService == null) {
            healthService = Service.lookup(HealthService.class);
        }
        return healthService;
    }
}
