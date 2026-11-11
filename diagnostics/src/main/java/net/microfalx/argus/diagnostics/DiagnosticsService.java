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

/**
 * Collects diagnostics information about the system and the application.
 */
@Slf4j
public class DiagnosticsService extends AbstractService {

    private volatile Store<Diagnostics, String> store;
    private Resource diagnostics;

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

    @Override
    public void initialize(Object... context) {

    }

    @Override
    public void start() {

    }

    private Resource getResource(Diagnostics.Type type) {
        if (diagnostics == null) {
            diagnostics = Resource.file(JvmUtils.getVariableDirectory("diagnostics"));
        }
        return diagnostics.resolve(type.name().toLowerCase(), Resource.Type.DIRECTORY);
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
}
