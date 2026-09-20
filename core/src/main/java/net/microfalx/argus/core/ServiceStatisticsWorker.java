package net.microfalx.argus.core;

import lombok.extern.slf4j.Slf4j;
import net.microfalx.argus.api.HealthService;
import net.microfalx.jvm.ObjectSize;
import net.microfalx.jvm.VirtualMachineMetrics;
import net.microfalx.lang.service.Service;
import net.microfalx.lang.service.ServiceLocator;
import net.microfalx.metrics.statistics.MutableStatisticalSummary;

@Slf4j
public class ServiceStatisticsWorker implements Runnable {

    private HealthService healthService;

    @Override
    public void run() {
        HealthServiceImpl.METRICS.time("Service Memory", (t) -> {
            updateStatistics();
        });
    }

    private void updateStatistics() {
        ServiceLocator.getServices().forEach(this::updateStatistics);
    }

    private void updateStatistics(Service service) {
        try {
            updateMemoryUsage(service);
        } catch (Throwable e) {
            LOGGER.atWarn().setCause(e).log("Failed to update statistics for service {}", service.getName());
        }
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

        trend = (MutableStatisticalSummary) getHealthService().getTrend(service, Service.Metric.MEMORY_ARRAY_USAGE);
        trend.add(memoryUsage.getArraySizeOf());
        ServiceLocator.report(service, Service.Metric.MEMORY_ARRAY_USAGE, memoryUsage.getArraySizeOf());

        trend = (MutableStatisticalSummary) getHealthService().getTrend(service, Service.Metric.MEMORY_ARRAY_OBJECTS);
        trend.add(memoryUsage.getArrayCountOf());
        ServiceLocator.report(service, Service.Metric.MEMORY_ARRAY_OBJECTS, memoryUsage.getArrayCountOf());
    }

    private HealthService getHealthService() {
        if (healthService == null) {
            healthService = Service.lookup(HealthService.class);
        }
        return healthService;
    }
}
