package net.microfalx.argus.core;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.microfalx.argus.api.*;
import net.microfalx.jvm.ServerMetrics;
import net.microfalx.jvm.VirtualMachineMetrics;
import net.microfalx.lang.*;
import net.microfalx.metrics.Batch;
import net.microfalx.metrics.SeriesStore;
import net.microfalx.registry.Data;
import net.microfalx.registry.Registry;
import net.microfalx.threadpool.Trigger;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;

import static java.lang.System.currentTimeMillis;
import static net.microfalx.jvm.VirtualMachineUtils.METRICS_METRICS;
import static net.microfalx.lang.ArgumentUtils.requireNonNull;
import static net.microfalx.lang.ClassUtils.resolveProviderInstances;
import static net.microfalx.lang.CollectionUtils.immutableCollection;
import static net.microfalx.lang.CollectionUtils.immutableSet;
import static net.microfalx.lang.StringUtils.defaultIfNull;

@Slf4j
public class HealthServiceImpl extends AbstractService implements HealthService {

    private static final String REGISTRY_PATH = "/health";

    private volatile Collection<HealthContributor> contributors = Collections.emptyList();
    private final Collection<HealthContributor> registeredContributors = new CopyOnWriteArraySet<>();
    private final Collection<HealthContributor> classPathContributors = new CopyOnWriteArraySet<>();

    private final Map<String, Thresholds> thresholds = new ConcurrentHashMap<>();
    private volatile boolean memory = true;
    private volatile SeriesStore seriesStore;
    private volatile net.microfalx.argus.api.Service service;

    @Getter private final Map<Resource.Type, Health> healths = new ConcurrentHashMap<>();

    @Override
    public net.microfalx.argus.api.Service getService() {
        if (service == null) {
            service = net.microfalx.argus.api.Service.create(getInstanceId(), getSlotId())
                    .withSlotId(getSlotId()).withInstanceId(getInstanceId());
        }
        return service;
    }

    @Override
    public void setService(net.microfalx.argus.api.Service service) {
        requireNonNull(service);
        this.service = service;
    }

    @Override
    public SeriesStore getStore() {
        return seriesStore;
    }

    @Override
    public Health getHealth(Resource.Type type) {
        requireNonNull(type);
        return healths.computeIfAbsent(type, t -> new Health());
    }

    @Override
    public Health getHealth(Health.Type type) {
        requireNonNull(type);
        return switch (type) {
            case INSTANCE -> getHealth(Resource.Type.SERVICE);
            case RESOURCE -> getServiceHealth();
            case SITE -> getSiteHealth();
        };
    }

    @Override
    public Collection<Resource> getResources(Resource.Type type) {
        requireNonNull(type);
        Collection<Resource> resources = new ArrayList<>();
        Iterable<Data> datas = Registry.get().list(getRegistryPath(type));
        datas.forEach(d -> resources.add(d.get()));
        return resources;
    }

    /**
     * Changes the location of the store metrics.
     *
     * @param memory {@code true} to store in memory, {@code false} to store on disk
     */
    public void setMemory(boolean memory) {
        this.memory = memory;
        initializeStore();
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
    public Set<Thresholds> getThresholds() {
        Set<Thresholds> thresholds = new HashSet<>();
        for (HealthContributor contributor : contributors) {
            thresholds.addAll(contributor.getThresholds());
        }
        return immutableSet(thresholds);
    }

    @Override
    public void register(Thresholds threshold) {
        requireNonNull(threshold);
        thresholds.put(threshold.getName(), threshold);
        LOGGER.debug("Register health threshold - {}", threshold.getName());
    }

    @Override
    public void register(HealthContributor contributor) {
        requireNonNull(contributor);
        initializeContributor(contributor);
        registeredContributors.add(contributor);
        LOGGER.debug("Register health contributor - {}", ClassUtils.getName(contributor));
        updateContributors();
    }

    @Override
    public void unregister(HealthContributor contributor) {
        requireNonNull(contributor);
        registeredContributors.remove(contributor);
        LOGGER.debug("Unregister health contributor - {}", ClassUtils.getName(contributor));
        updateContributors();
    }

    @Override
    public void update() {
        updateHealth();
        updateMetrics();
        storeResources();
    }

    @Override
    public void initialize(Object... context) {
        discoverHealthContributors();
        updateContributors();
        initializeStore();
        initializeMetrics();
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
        getThreadPool().schedule(this::updateMetrics, Trigger.fixedDelay(Duration.ofSeconds(10)));
    }

    void maintenance() {
        updateContributorsStats();
    }

    void updateHealth() {
        for (Resource.Type type : Resource.Type.values()) {
            Health nextHealth = new Health();
            for (HealthContributor contributor : contributors) {
                if (!contributor.supports(type)) continue;
                METRICS_METRICS.time("Scrape Health " + contributor.getName(), (t) -> contributor.update(nextHealth));
            }
            this.healths.put(type, nextHealth);
        }
    }

    void updateMetrics() {
        Batch batch = Batch.create(currentTimeMillis());
        METRICS_METRICS.time("Scrape Health", t -> updateMetrics(batch));
        METRICS_METRICS.time("Store Health", t -> seriesStore.add(batch));
    }

    private void updateMetrics(Batch batch) {
        for (HealthContributor contributor : contributors) {
            contributor.update(batch);
        }
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

    private void initializeStore() {
        seriesStore = memory ? SeriesStore.memory() : SeriesStore.disk("health");
    }

    private void initializeMetrics() {
        VirtualMachineMetrics.get().setExecutor(getThreadPool());
        ServerMetrics.get().setExecutor(getThreadPool());
    }

    private Health getServiceHealth() {
        return new Health();
    }

    private Health getSiteHealth() {
        return new Health();
    }

    private void storeResources() {
        Registry registry = Registry.get();
        // first, store the service info
        Resource serviceResource = createServiceResource();
        registry.set(getRegistryPath(serviceResource), serviceResource);
        // second, store the server info
        Resource serverResource = createServerResource();
        registry.set(getRegistryPath(serverResource), serverResource);
    }

    private String getRegistryPath(Resource.Type type) {
        requireNonNull(type);
        return REGISTRY_PATH + "/" + type.name().toLowerCase();
    }

    private String getRegistryPath(Resource resource) {
        requireNonNull(resource);
        return getRegistryPath(resource.getType()) + "/" + resource.getId();
    }

    private String getSlotId() {
        return defaultIfNull(System.getenv().get("ARGUS_SERVICE_SLOT_ID"), "1");
    }

    private String getInstanceName() {
        return defaultIfNull(System.getenv().get("ARGUS_SERVICE_INSTANCE_NAME"), "Default");
    }

    private String getInstanceId() {
        return defaultIfNull(System.getenv().get("ARGUS_SERVICE_INSTANCE_ID"), IdGenerator.get().nextAsString());
    }

    private String getClusterName() {
        return defaultIfNull(System.getenv().get("ARGUS_CLUSTER"), "Default");
    }

    private String getHostname() {
        return defaultIfNull(System.getenv().get("ARGUS_HOSTNAME"), JvmUtils.getLocalHost().getHostName());
    }

    private Resource createServiceResource() {
        return (Resource) Resource.create(Resource.Type.SERVICE, getSlotId())
                .withGroup(getService().getName()).withHealth(getHealth(Resource.Type.SERVICE))
                .withName(getService().getName() + " " + service.getSlotId());
    }

    private Resource createServerResource() {
        return (Resource) Resource.create(Resource.Type.SERVER, getHostname())
                .withGroup(getClusterName()).withHealth(getHealth(Resource.Type.SERVER))
                .withName(getHostname());
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
            update();
        }
    }
}
