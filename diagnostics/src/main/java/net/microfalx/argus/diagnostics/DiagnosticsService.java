package net.microfalx.argus.diagnostics;

import lombok.extern.slf4j.Slf4j;
import net.microfalx.argus.api.Health;
import net.microfalx.argus.api.HealthListener;
import net.microfalx.argus.api.HealthService;
import net.microfalx.argus.api.Thresholds;
import net.microfalx.argus.core.AbstractService;
import net.microfalx.lang.JvmUtils;
import net.microfalx.lang.RichText;
import net.microfalx.lang.annotation.Provider;
import net.microfalx.resource.Resource;
import net.microfalx.service.api.Service;
import net.microfalx.store.api.Query;
import net.microfalx.store.api.Store;
import net.microfalx.store.api.StoreService;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static java.lang.Integer.MAX_VALUE;
import static net.microfalx.lang.ArgumentUtils.requireNonNull;
import static net.microfalx.lang.StringUtils.isNotEmpty;

/**
 * Collects diagnostics information about the system and the application.
 * <p>
 * The service also listens for health changes and collects diagnostics automatically when a resource
 * enters the red zone:
 * <ul>
 *     <li>a {@link Histogram heap histogram} when the JVM memory is the cause</li>
 *     <li>a (CPU) {@link Profile profile} when the server CPU is the cause</li>
 * </ul>
 */
@Slf4j
@Provider
public class DiagnosticsService extends AbstractService implements HealthListener {

    private static final String MEMORY_THRESHOLDS_PREFIX = "jvm.memory.";
    private static final String CPU_THRESHOLDS_PREFIX = "server.cpu.";

    private volatile Store<Diagnostics, String> store;
    private Resource diagnostics;

    private final AtomicBoolean histogramInProgress = new AtomicBoolean();
    private final AtomicBoolean profileInProgress = new AtomicBoolean();

    public static DiagnosticsService getInstance() {
        return Service.lookup(DiagnosticsService.class);
    }

    /**
     * Returns the resource where diagnostics are stored.
     *
     * @return a non-null instance
     */
    public Resource getResources() {
        if (diagnostics == null) {
            diagnostics = Resource.file(JvmUtils.getVariableDirectory("diagnostics"));
        }
        return diagnostics;
    }

    /**
     * Returns the resource where diagnostics of a given type are stored.
     *
     * @param type the diagnostics type
     * @return a non-null instance
     */
    public Resource getResources(Diagnostics.Type type) {
        requireNonNull(type);
        return getResources().resolve(type.name().toLowerCase(), Resource.Type.DIRECTORY);
    }

    /**
     * Returns all diagnostics collected by the service.
     *
     * @return a non-null instance
     */
    public Collection<Diagnostics> getDiagnostics() {
        return getStore().list(Query.<Diagnostics>builder().build());
    }

    /**
     * Returns all diagnostics collected by the service for a given type, between the specified start and end date.
     *
     * @param type  the diagnostics type
     * @param start the start date
     * @param end   the end date
     * @return a non-null instance
     */
    public Collection<Diagnostics> getDiagnostics(Diagnostics.Type type, LocalDateTime start, LocalDateTime end) {
        return getStore().list(Query.<Diagnostics>builder().start(start).end(end).build()).stream()
                .filter(diagnostics -> diagnostics.getType() == type)
                .toList();
    }

    /**
     * Starts a new diagnostics of the specified type, with default options, and adds it to the store.
     *
     * @param type the diagnostics type
     * @param name the name of the diagnostics
     * @return a non-null instance
     */
    public Diagnostics start(Diagnostics.Type type, String name) {
        return start(type, name, null);
    }

    /**
     * Starts a new diagnostics of the specified type and adds it to the store.
     *
     * @param type    the diagnostics type
     * @param name    the name of the diagnostics
     * @param options the options specific to the diagnostics type (like {@link HeapDump.Options}),
     *                null to use the default options
     * @return a non-null instance
     */
    public Diagnostics start(Diagnostics.Type type, String name, Diagnostics.Options options) {
        return start(type, name, null, options);
    }

    /**
     * Starts a new diagnostics of the specified type and adds it to the store.
     *
     * @param type        the diagnostics type
     * @param name        the name of the diagnostics
     * @param description the description of the diagnostics (usually the reason why it was collected), can be null
     * @param options     the options specific to the diagnostics type (like {@link HeapDump.Options}),
     *                    null to use the default options
     * @return a non-null instance
     */
    public Diagnostics start(Diagnostics.Type type, String name, String description, Diagnostics.Options options) {
        Diagnostics diagnostics = create(type, name, options);
        diagnostics.updateDescription(description);
        diagnostics.diagnosticsService = this;
        getStore().add(diagnostics);
        return diagnostics;
    }

    @Override
    public void initialize(Object... context) {

    }

    @Override
    public void start() {
        registerHealthListener();
    }

    @Override
    public void onUnhealthy(net.microfalx.argus.api.Resource resource) {
        Health health = resource.getHealth();
        if (health == null) return;
        switch (resource.getType()) {
            case SERVICE -> {
                List<Health.Item> items = getCriticalItems(health, MEMORY_THRESHOLDS_PREFIX);
                if (!items.isEmpty()) {
                    collect(Diagnostics.Type.HISTOGRAM, getName("Memory", resource, items),
                            getDescription("JVM memory", resource, health, items), histogramInProgress);
                }
            }
            case SERVER -> {
                List<Health.Item> items = getCriticalItems(health, CPU_THRESHOLDS_PREFIX);
                if (!items.isEmpty()) {
                    collect(Diagnostics.Type.PROFILE, getName("CPU", resource, items),
                            getDescription("Server CPU", resource, health, items), profileInProgress);
                }
            }
            default -> {
                // no diagnostics for other resources
            }
        }
    }

    @Override
    public void onHealthy(net.microfalx.argus.api.Resource resource) {
        LOGGER.debug("Resource '{}' is back to normal", resource.getName());
    }

    void update(Diagnostics diagnostics) {
        try {
            getStore().add(diagnostics);
        } catch (Exception e) {
            LOGGER.atError().setCause(e).log("Failed to update diagnostics '{}'", diagnostics.getName());
        }
    }

    private void registerHealthListener() {
        try {
            HealthService.getInstance().register(this);
        } catch (Exception e) {
            LOGGER.atWarn().setCause(e).log("Failed to register diagnostics as health listener, " +
                    "diagnostics will not be collected automatically");
        }
    }

    /**
     * Collects (asynchronously) a diagnostics, if one of the same kind is not already in progress.
     *
     * @param type       the diagnostics type
     * @param name        the name of the diagnostics
     * @param description the reason why the diagnostics is collected
     * @param inProgress  the flag which guards against concurrent collections of the same kind
     */
    private void collect(Diagnostics.Type type, String name, String description, AtomicBoolean inProgress) {
        if (!inProgress.compareAndSet(false, true)) {
            LOGGER.info("Skip diagnostics '{}', a {} is already in progress", name, type);
            return;
        }
        LOGGER.info("Collect diagnostics '{}' ({})", name, type);
        try {
            getThreadPool().execute(() -> {
                try {
                    Diagnostics diagnostics = start(type, name, description, null);
                    diagnostics.collect();
                    update(diagnostics);
                    if (diagnostics.getFailure() != null) {
                        LOGGER.warn("Diagnostics '{}' failed: {}", name, diagnostics.getFailure());
                    } else {
                        LOGGER.info("Diagnostics '{}' collected in {}", name, diagnostics.getDuration());
                    }
                } catch (Exception e) {
                    LOGGER.atError().setCause(e).log("Failed to collect diagnostics '{}'", name);
                } finally {
                    inProgress.set(false);
                }
            });
        } catch (Exception e) {
            inProgress.set(false);
            LOGGER.atError().setCause(e).log("Failed to schedule diagnostics '{}'", name);
        }
    }

    private static List<Health.Item> getCriticalItems(Health health, String thresholdsPrefix) {
        return health.getWorstItems(MAX_VALUE).stream()
                .filter(item -> item.getScore() < Health.ERROR)
                .filter(item -> {
                    Thresholds thresholds = item.getThresholds();
                    return thresholds != null && isNotEmpty(thresholds.getId())
                            && thresholds.getId().startsWith(thresholdsPrefix);
                }).toList();
    }

    private static String getName(String cause, net.microfalx.argus.api.Resource resource, List<Health.Item> items) {
        String itemNames = items.stream().map(Health.Item::getName).collect(Collectors.joining(", "));
        return cause + " (" + itemNames + ") on " + resource.getName();
    }

    /**
     * Returns the reason why a diagnostics was triggered, based on the health of the resource.
     * <p>
     * The description looks like {@code JVM memory entered the red zone on <resource> (health score 25):
     * Heap: 12 (95% [3.8GB of 4GB]); Tenured: 10 (97% [2.9GB of 3GB])}.
     *
     * @param cause    the cause (resource kind) which triggered the diagnostics
     * @param resource the unhealthy resource
     * @param health   the health of the resource
     * @param items    the health items in the red zone
     * @return a non-null instance
     */
    private static String getDescription(String cause, net.microfalx.argus.api.Resource resource, Health health,
                                         List<Health.Item> items) {
        String itemsReport = items.stream().map(item -> item.toRichText().render(RichText.Format.TEXT))
                .collect(Collectors.joining("; "));
        return cause + " entered the red zone on " + resource.getName() + " (health score "
                + Health.scoreToRichText(health.getScore()).render(RichText.Format.TEXT) + "): " + itemsReport;
    }

    private Store<Diagnostics, String> getStore() {
        if (store != null) return store;
        synchronized (this) {
            if (store == null) {
                LOGGER.info("Initializing diagnostics store");
                Store.Options options = Store.Options.create("Diagnostics");
                store = StoreService.getInstance().register(options);
            }
            return store;
        }
    }

    private Diagnostics create(Diagnostics.Type type, String name, Diagnostics.Options options) {
        requireNonNull(type);
        return switch (type) {
            case HISTOGRAM -> new Histogram(name, getOptions(type, options, Histogram.Options.class,
                    Histogram.Options::create));
            case HEAP_DUMP -> new HeapDump(name, getOptions(type, options, HeapDump.Options.class,
                    HeapDump.Options::create));
            case THREAD_DUMP -> new ThreadDump(name, getOptions(type, options, ThreadDump.Options.class,
                    ThreadDump.Options::create));
            case VM_REPORT -> new VmReport(name, getOptions(type, options, VmReport.Options.class,
                    VmReport.Options::create));
            case FLIGHT_RECORDING -> new FlightRecording(name, getOptions(type, options, FlightRecording.Options.class,
                    FlightRecording.Options::create));
            case PROFILE -> new Profile(name, getOptions(type, options, Profile.Options.class,
                    Profile.Options::create));
        };
    }

    private static <O extends Diagnostics.Options> O getOptions(Diagnostics.Type type, Diagnostics.Options options,
                                                                Class<O> optionsClass, Supplier<O> defaults) {
        if (options == null) return defaults.get();
        if (!optionsClass.isInstance(options)) {
            throw new DiagnosticsException("Diagnostics of type " + type + " requires options of type "
                    + optionsClass.getName() + ", received " + options.getClass().getName());
        }
        return optionsClass.cast(options);
    }
}
