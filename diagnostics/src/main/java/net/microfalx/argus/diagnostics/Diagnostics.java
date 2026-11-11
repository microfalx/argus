package net.microfalx.argus.diagnostics;

import lombok.ToString;
import net.microfalx.lang.IdGenerator;
import net.microfalx.lang.NamedIdentityAware;
import net.microfalx.resource.Resource;

import java.io.IOException;
import java.net.URI;

import static net.microfalx.lang.ArgumentUtils.requireNonNull;
import static net.microfalx.lang.ArgumentUtils.requireNotEmpty;

/**
 * Base class for all diagnostics.
 */
@ToString(callSuper = true)
public abstract class Diagnostics extends NamedIdentityAware<String> {

    private static final long MAX_STORED_DIAGNOSTICS_SIZE = 1024 * 1024;

    private URI uri;
    private byte[] data;

    public Diagnostics(String name) {
        requireNotEmpty(name);
        setId(IdGenerator.get().nextAsString());
        setName(name);
    }

    /**
     * Returns the resource associated with this diagnostics.
     *
     * @return a non-null instance
     */
    public Resource getResource() {
        if (data != null) return Resource.bytes(data);
        if (uri != null) return Resource.uri(uri);
        throw new DiagnosticsNotFoundException("Diagnostics resource is not available for " + getName());
    }

    /**
     * Returns the type of diagnostics.
     *
     * @return a non-null instance
     */
    protected abstract Type getType();

    /**
     * Collects the diagnostics data.
     */
    protected abstract void collect();

    /**
     * Updates the resource associated with this diagnostics.
     *
     * @param resource the resource
     * @throws IOException if an I/O error occurs
     */
    protected void updateResource(Resource resource) throws IOException {
        requireNonNull(resource);
        this.uri = resource.toURI();
        if (resource.length() <= MAX_STORED_DIAGNOSTICS_SIZE) {
            data = resource.loadAsBytes();
        }
    }

    public enum Type {
        PROFILE,
        HEAP_DUMP,
        HISTOGRAM
    }
}
