package net.microfalx.argus.core;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.microfalx.argus.api.Health;
import net.microfalx.argus.api.HealthContributor;
import net.microfalx.argus.api.HealthService;
import net.microfalx.argus.api.Thresholds;
import net.microfalx.lang.ClassUtils;
import net.microfalx.lang.Initializable;
import net.microfalx.threadpool.Trigger;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;

import static net.microfalx.lang.ArgumentUtils.requireNonNull;
import static net.microfalx.lang.ClassUtils.resolveProviderInstances;
import static net.microfalx.lang.CollectionUtils.immutableCollection;

@Slf4j
public class HealthServiceImpl extends AbstractService implements HealthService {

    private volatile Collection<HealthContributor> contributors = Collections.emptyList();
    private final Collection<HealthContributor> registeredContributors = new CopyOnWriteArraySet<>();
    private final Collection<HealthContributor> classPathContributors = new CopyOnWriteArraySet<>();

    @Getter private volatile Health health = new Health();

    @Override
    public void initialize(Object... context) {
        discoverHealthContributors();
        updateContributors();
    }

    @Override
    public void start() {
        registerTasks();
    }

    @Override
    public Collection<HealthContributor> getContributors() {
        return immutableCollection(contributors);
    }

    @Override
    public Collection<Thresholds> getThresholds() {
        Collection<Thresholds> thresholds = new ArrayList<>();
        for (HealthContributor contributor : contributors) {
            thresholds.addAll(contributor.getThresholds());
        }
        return immutableCollection(thresholds);
    }

    @Override
    public void registerContributor(HealthContributor contributor) {
        requireNonNull(contributor);
        initializeContributor(contributor);
        registeredContributors.add(contributor);
        LOGGER.debug("Register health contributor - {}", ClassUtils.getName(contributor));
        updateContributors();
    }

    @Override
    public void unregisterContributor(HealthContributor contributor) {
        requireNonNull(contributor);
        registeredContributors.remove(contributor);
        LOGGER.debug("Unregister health contributor - {}", ClassUtils.getName(contributor));
    }

    private void discoverHealthContributors() {
        LOGGER.info("Discover health contributors");
        for (HealthContributor contributor : resolveProviderInstances(HealthContributor.class)) {
            LOGGER.debug(" - {}", ClassUtils.getName(contributor));
            initializeContributor(contributor);
            classPathContributors.add(contributor);
        }
        LOGGER.info("Discovered {} health contributors", classPathContributors.size());
    }

    private void updateContributors() {
        Collection<HealthContributor> updatedContributors = new ArrayList<>(classPathContributors);
        updatedContributors.addAll(registeredContributors);
        this.contributors = updatedContributors;
    }

    private void registerTasks() {
        getThreadPool().schedule(new MaintenanceTask(), Trigger.fixedDelay(Duration.ofMinutes(15)));
        getThreadPool().schedule(new ScrapeTask(), Trigger.fixedDelay(Duration.ofSeconds(30)));
    }

    void maintenance() {
        updateContributorsStats();
    }

    void scrape() {
        Health nextHealth = new Health();
        for (HealthContributor contributor : contributors) {
            contributor.update(nextHealth);
        }
        this.health = nextHealth;
    }

    private void updateContributorsStats() {
        for (HealthContributor contributor : contributors) {
            contributor.updateStats();
        }
    }

    private void initializeContributor(HealthContributor contributor) {
        if (contributor instanceof Initializable) {
            ((Initializable) contributor).initialize();
        }
    }

    private class MaintenanceTask implements Runnable {
        @Override
        public void run() {
            maintenance();
        }
    }

    private class ScrapeTask implements Runnable {
        @Override
        public void run() {
            scrape();
        }
    }
}
