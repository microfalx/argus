package net.microfalx.argus.core;

import lombok.extern.slf4j.Slf4j;
import net.microfalx.argus.api.HealthService;
import net.microfalx.jvm.ObjectSize;
import net.microfalx.jvm.VirtualMachineMetrics;
import net.microfalx.lang.service.Service;
import net.microfalx.lang.service.ServiceLocator;
import net.microfalx.metrics.Timer;
import net.microfalx.metrics.statistics.MutableStatisticalSummary;

import java.util.Collection;

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
        Collection<Service> services = ServiceLocator.getServices();
        Collection<Service> serviceProxies = ServiceLocator.getServiceProxies();
        LOGGER.info("Update statistics for {} services", services.size() + serviceProxies.size());
        LOGGER.debug("Update statistics for {} internal services", services.size());
        services.forEach(this::updateStatistics);
        LOGGER.debug("Update statistics for {} external services", serviceProxies.size());
        serviceProxies.forEach(this::updateStatistics);
        LOGGER.info("Completed update statistics for {} services in {}",
                services.size() + serviceProxies.size(), Timer.currentDuration());
    }

    private void updateStatistics(Service service) {
        try {
            updateMemoryUsage(service);
        } catch (Throwable e) {
            LOGGER.atWarn().setCause(e).log("Failed to update statistics for service {}", service.getName());
        }
    }

    private void updateMemoryUsage(Service service) {
        LOGGER.debug("Update memory usage for service {}", service.getName());
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
