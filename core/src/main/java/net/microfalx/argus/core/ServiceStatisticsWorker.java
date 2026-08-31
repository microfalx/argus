package net.microfalx.argus.core;

import net.microfalx.jvm.VirtualMachineMetrics;
import net.microfalx.lang.service.Service;
import net.microfalx.lang.service.ServiceLocator;
import net.microfalx.lang.service.ServiceStatistics;

public class ServiceStatisticsWorker implements Runnable {

    @Override
    public void run() {
        ServiceLocator.getServices().forEach(this::updateStatistics);
    }

    private void updateStatistics(Service service) {
        Service.Statistics<?> statistics = ServiceLocator.getStatistics(service);
        if (!(statistics instanceof ServiceStatistics<?> serviceStatistics)) return;
        updateMemoryUsage(serviceStatistics);
    }

    private void updateMemoryUsage(ServiceStatistics<?> statistics) {
        Object realService = ServiceLocator.getRealService(statistics.getService());
        long memoryUsage = VirtualMachineMetrics.get().getDeepSize(realService);
        statistics.setMemoryUsage(memoryUsage);
    }
}
