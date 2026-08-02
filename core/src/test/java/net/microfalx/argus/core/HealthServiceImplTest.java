package net.microfalx.argus.core;

import net.microfalx.argus.api.*;
import net.microfalx.jvm.ServerMetrics;
import net.microfalx.jvm.VirtualMachineMetrics;
import net.microfalx.metrics.Metric;
import net.microfalx.threadpool.ThreadPool;
import net.microfalx.threadpool.Trigger;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class HealthServiceImplTest {

    @Mock
    private ThreadPool threadPool;

    private HealthServiceImpl healthService;

    @BeforeEach
    void setup() {
        healthService = new HealthServiceImpl();
        healthService.setThreadPool(threadPool);
        healthService.initialize();
        healthService.setMemory(true);
        healthService.start();

        scrape();
    }

    @Test
    void getInstance() {
        HealthService instance = HealthService.getInstance();
        assertNotNull(instance);
    }

    @Test
    void getStore() {
        HealthService instance = HealthService.getInstance();
        assertNotNull(instance.getStore());
    }

    @Test
    void checkRegisteredTasks() {
        verify(threadPool, times(3)).schedule(any(Runnable.class), any(Trigger.class));
    }

    @Test
    void startSchedulesMaintenanceAndScrapeTasksOnThreadPool() {
        RecordingContributor contributor = new RecordingContributor();
        healthService.register(contributor);

        healthService.maintenance();
        healthService.updateHealth();

        assertEquals(1, contributor.updateStatsCount.get());
        assertEquals(1, contributor.updateCount.get());
    }

    @Test
    void maintenanceInvokesUpdateStatsForAllContributors() {
        RecordingContributor first = new RecordingContributor();
        RecordingContributor second = new RecordingContributor();
        healthService.register(first);
        healthService.register(second);

        healthService.maintenance();

        assertEquals(1, first.updateStatsCount.get());
        assertEquals(1, second.updateStatsCount.get());
        assertEquals(0, first.updateCount.get());
        assertEquals(0, second.updateCount.get());
    }

    @Test
    void scrapeInvokesContributorsAndUpdatesHealth() {
        RecordingContributor contributor = new RecordingContributor();
        healthService.register(contributor);

        healthService.updateHealth();

        assertEquals(1, contributor.updateCount.get());
        assertTrue(healthService.getHealth(Resource.Type.SERVICE)
                .getGroup("Test Group").getItems().stream()
                .anyMatch(item -> "Test Item".equals(item.getName())));
    }

    @Test
    void scrapeInvokesContributorsAndUpdatesMetrics() {
        RecordingContributor contributor = new RecordingContributor();
        healthService.register(contributor);

        healthService.updateMetrics();

        assertEquals(2, healthService.getStore().getMetrics().size());
        assertEquals(1, healthService.getStore().get(Metric.get("jvm.score")).getCount());
        assertEquals(1, healthService.getStore().get(Metric.get("server.score")).getCount());
    }

    @Test
    void scrapeAndReport() {
        for (int i = 0; i < 5; i++) {
            healthService.updateHealth();
            scrape();
        }
        String report = healthService.getHealth(Resource.Type.SERVICE).getReport();
        Assertions.assertThat(report).contains("Total:")
                .contains("JVM").contains("Memory")
                .contains("Eden:").contains("Metaspace:")
                .contains("GC")
                .contains("Other");
    }

    private void scrape() {
        VirtualMachineMetrics.get().scrape();
        ServerMetrics.get().scrape();
    }

    private static final class RecordingContributor implements HealthContributor {

        private final AtomicInteger updateCount = new AtomicInteger();
        private final AtomicInteger updateStatsCount = new AtomicInteger();

        @Override
        public String getName() {
            return "Test";
        }

        @Override
        public boolean supports(Resource.Type type) {
            return true;
        }

        @Override
        public void update(Health health) {
            updateCount.incrementAndGet();
            health.update("Test Group", "Test Item", 4f);
        }

        @Override
        public void updateStats() {
            updateStatsCount.incrementAndGet();
        }

        @Override
        public Collection<Thresholds> getThresholds() {
            return List.of();
        }
    }

}