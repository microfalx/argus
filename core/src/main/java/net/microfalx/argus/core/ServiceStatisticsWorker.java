package net.microfalx.argus.core;

import net.microfalx.jvm.VirtualMachineMetrics;
import net.microfalx.lang.service.Service;
import net.microfalx.lang.service.ServiceLocator;

public class ServiceStatisticsWorker implements Runnable {

    @Override
    public void run() {
        ServiceLocator.getServices().forEach(this::updateStatistics);
    }

    private void updateStatistics(Service service) {
        updateMemoryUsage(service);
    }

    private void updateMemoryUsage(Service service) {
        Object realService = ServiceLocator.getRealService(service);
        long memoryUsage = VirtualMachineMetrics.get().getDeepSize(realService);
        ServiceLocator.report(service, Service.Event.MEMORY_USAGE, memoryUsage);
    }
}
