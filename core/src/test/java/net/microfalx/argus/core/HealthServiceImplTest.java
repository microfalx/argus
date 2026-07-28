package net.microfalx.argus.core;

import net.microfalx.argus.api.Health;
import net.microfalx.argus.api.HealthContributor;
import net.microfalx.argus.api.HealthService;
import net.microfalx.argus.api.Thresholds;
import net.microfalx.jvm.ServerMetrics;
import net.microfalx.jvm.VirtualMachineMetrics;
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
        healthService.start();

        scrape();
    }

    @Test
    void getInstance() {
        HealthService instance = HealthService.getInstance();
        assertNotNull(instance);
    }

    @Test
    void checkRegisteredTasks() {
        verify(threadPool, times(2)).schedule(any(Runnable.class), any(Trigger.class));
    }

    @Test
    void startSchedulesMaintenanceAndScrapeTasksOnThreadPool() {
        RecordingContributor contributor = new RecordingContributor();
        healthService.registerContributor(contributor);

        healthService.maintenance();
        healthService.scrape();


        assertEquals(1, contributor.updateStatsCount.get());
        assertEquals(1, contributor.updateCount.get());
    }

    @Test
    void maintenanceInvokesUpdateStatsForAllContributors() {
        RecordingContributor first = new RecordingContributor();
        RecordingContributor second = new RecordingContributor();
        healthService.registerContributor(first);
        healthService.registerContributor(second);

        healthService.maintenance();

        assertEquals(1, first.updateStatsCount.get());
        assertEquals(1, second.updateStatsCount.get());
        assertEquals(0, first.updateCount.get());
        assertEquals(0, second.updateCount.get());
    }

    @Test
    void scrapeInvokesContributorsAndUpdatesHealth() {
        RecordingContributor contributor = new RecordingContributor();
        healthService.registerContributor(contributor);

        healthService.scrape();

        assertEquals(1, contributor.updateCount.get());
        assertTrue(healthService.getHealth().getGroup("Test Group").getItems().stream()
                .anyMatch(item -> "Test Item".equals(item.getName())));
    }

    @Test
    void scrapeAndReport() {
        for (int i = 0; i < 5; i++) {
            healthService.scrape();
            scrape();
        }
        String report = healthService.getHealth().getReport();
        Assertions.assertThat(report).contains("Total:")
                .contains("JVM / Memory")
                .contains("Eden:").contains("Metaspace:")
                .contains("JVM / GC")
                .contains("JVM / Other");
    }

    private void scrape() {
        VirtualMachineMetrics.get().scrape();
        ServerMetrics.get().scrape();
    }

    private static final class RecordingContributor implements HealthContributor {

        private final AtomicInteger updateCount = new AtomicInteger();
        private final AtomicInteger updateStatsCount = new AtomicInteger();

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