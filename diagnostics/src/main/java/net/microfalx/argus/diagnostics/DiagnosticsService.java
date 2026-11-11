package net.microfalx.argus.diagnostics;

import lombok.extern.slf4j.Slf4j;
import net.microfalx.argus.core.AbstractService;
import net.microfalx.lang.JvmUtils;
import net.microfalx.resource.Resource;
import net.microfalx.store.api.Query;
import net.microfalx.store.api.Store;
import net.microfalx.store.api.StoreService;

import java.time.LocalDateTime;
import java.util.Collection;

import static net.microfalx.lang.ArgumentUtils.requireNonNull;

/**
 * Collects diagnostics information about the system and the application.
 */
@Slf4j
public class DiagnosticsService extends AbstractService {

    private volatile Store<Diagnostics, String> store;
    private Resource diagnostics;

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
        return diagnostics.resolve(type.name().toLowerCase(), Resource.Type.DIRECTORY);
    }

    /**
     * Returns all diagnostics collected by the service.
     *
     * @return a non-null instance
     */
    public Collection<Diagnostics> getDiagnostics() {
        return store.list(Query.<Diagnostics>builder().build());
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
        return store.list(Query.<Diagnostics>builder().start(start).end(end).build()).stream()
                .filter(diagnostics -> diagnostics.getType() == type)
                .toList();
    }

    /**
     * Starts a new diagnostics of the specified type and adds it to the store.
     *
     * @param type the diagnostics type
     * @return a non-null instance
     */
    public Diagnostics start(Diagnostics.Type type) {
        Diagnostics diagnostics = create(type);
        store.add(diagnostics);
        return diagnostics;
    }

    @Override
    public void initialize(Object... context) {

    }

    @Override
    public void start() {

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

    private Diagnostics create(Diagnostics.Type type) {
        return switch (type) {
            case HISTOGRAM -> new Histogram(type.name());
            case HEAP_DUMP -> new HeapDump(type.name());
            case PROFILE -> new Profile(type.name());
            default -> throw new IllegalArgumentException("Unsupported diagnostics type: " + type);
        };
    }
}
