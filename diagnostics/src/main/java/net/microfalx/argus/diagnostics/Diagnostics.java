package net.microfalx.argus.diagnostics;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.ToString;
import net.microfalx.argus.api.Failure;
import net.microfalx.lang.EnumUtils;
import net.microfalx.lang.IdGenerator;
import net.microfalx.lang.NamedIdentityAware;
import net.microfalx.resource.Resource;

import java.io.IOException;
import java.net.URI;
import java.time.Duration;
import java.time.ZonedDateTime;

import static net.microfalx.lang.ArgumentUtils.requireNonNull;

/**
 * Base class for all diagnostics.
 */
@Getter
@ToString(callSuper = true)
public abstract class Diagnostics extends NamedIdentityAware<String> {

    private static final long MAX_STORED_DIAGNOSTICS_SIZE = 1024 * 1024;

    /**
     * The URI of the resource associated with this diagnostics. This can be a file, a URL or any other resource.
     */
    private URI uri;

    /**
     * The data of the resource associated with this diagnostics. This is used to store the diagnostics data in memory
     * if the size of the resource is less than {@link #MAX_STORED_DIAGNOSTICS_SIZE}.
     */
    private byte[] data;

    /**
     * The failure associated with this diagnostics. This is used to store the diagnostics failure in case of an error.
     */
    private Failure failure;

    /**
     * Returns the start time of the diagnostics. This is used to store the time when the diagnostics was started.
     */
    private ZonedDateTime startTime;

    /**
     * Returns the end time of the diagnostics. This is used to store the time when the diagnostics was completed.
     */
    private ZonedDateTime endTime;

    @Getter(AccessLevel.NONE)
    transient DiagnosticsService diagnosticsService;

    public Diagnostics() {
        setId(IdGenerator.get().nextAsString());
        setName(EnumUtils.toLabel(getType()));
    }

    /**
     * Returns the duration of the diagnostics. This is the time between the start and end time of the diagnostics.
     *
     * @return the duration, null if it was not started or completed
     */
    public Duration getDuration() {
        if (startTime == null && endTime == null) return null;
        if (startTime != null && endTime == null) {
            return Duration.between(startTime, ZonedDateTime.now());
        } else {
            return Duration.between(startTime, endTime);
        }
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
     * Collects the diagnostics data and stores it in the resource associated with this diagnostics.
     */
    public void collect() {
        startTime = ZonedDateTime.now();
        try {
            collect(diagnosticsService.getResources(getType()));
        } catch (Exception e) {
            failure = Failure.of(e);
        } finally {
            endTime = ZonedDateTime.now();
        }
    }

    /**
     * Returns the type of diagnostics.
     *
     * @return a non-null instance
     */
    protected abstract Type getType();

    /**
     * Collects the diagnostics data.
     *
     * @param diagnostics the resource where the diagnostics data should be stored
     * @throws Exception if an  error occurs
     */
    protected abstract void collect(Resource diagnostics) throws Exception;

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
