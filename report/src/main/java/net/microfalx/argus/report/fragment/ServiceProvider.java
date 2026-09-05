package net.microfalx.argus.report.fragment;

import lombok.ToString;
import net.microfalx.argus.report.AbstractFragmentProvider;
import net.microfalx.argus.report.Fragment;
import net.microfalx.argus.report.Template;
import net.microfalx.lang.annotation.Provider;
import net.microfalx.lang.service.Service;
import net.microfalx.lang.service.ServiceLocator;

import java.util.Comparator;
import java.util.List;

@Provider
public class ServiceProvider extends AbstractFragmentProvider {

    @Override
    public Fragment create() {
        return Fragment.builder("Services").template("service")
                .icon("fa-solid fa-puzzle-piece")
                .order(30)
                .build();
    }

    @Override
    public void update(Template template) {
        doUpdate(template);
    }

    static void doUpdate(Template template) {
        List<ServiceStatistics<Service>> statistics = ServiceLocator.getServices().stream()
                .map(service -> new ServiceStatistics<>(ServiceLocator.getStatistics(service)))
                .sorted(Comparator.comparing(ServiceStatistics::getName))
                .toList();
        template.addVariable("services", statistics);
    }

    @ToString
    public static class ServiceStatistics<S extends Service> implements Service.Statistics<S> {

        private final Service.Statistics<S> statistics;

        public ServiceStatistics(Service.Statistics<S> statistics) {
            this.statistics = statistics;
        }

        @Override
        public String getId() {
            return statistics.getId();
        }

        @Override
        public String getName() {
            return statistics.getName();
        }

        @Override
        public String getDescription() {
            return statistics.getDescription();
        }

        @Override
        public S getService() {
            return statistics.getService();
        }

        @Override
        public String getClassName() {
            return statistics.getClassName();
        }

        public long getMemoryUsage() {
            return statistics.getMemoryUsage();
        }

        @Override
        public int getWarningCount() {
            return statistics.getWarningCount();
        }

        @Override
        public int getErrorCount() {
            return statistics.getErrorCount();
        }

        @Override
        public int getSuccessCount() {
            return statistics.getSuccessCount();
        }

        @Override
        public int getFailedCount() {
            return statistics.getFailedCount();
        }

        @Override
        public int getEventInCount() {
            return statistics.getEventInCount();
        }

        @Override
        public int getEventOutCount() {
            return statistics.getEventOutCount();
        }

        @Override
        public int getTaskRunningCount() {
            return statistics.getTaskRunningCount();
        }

        @Override
        public int getTaskPendingCount() {
            return statistics.getTaskPendingCount();
        }

        @Override
        public int getThreadCount() {
            return statistics.getThreadCount();
        }
    }
}
