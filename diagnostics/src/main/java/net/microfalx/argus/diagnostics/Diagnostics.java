package net.microfalx.argus.diagnostics;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.ToString;
import lombok.experimental.SuperBuilder;
import net.microfalx.argus.api.Failure;
import net.microfalx.lang.EnumUtils;
import net.microfalx.lang.IdGenerator;
import net.microfalx.lang.NamedIdentityAware;
import net.microfalx.resource.Resource;
import net.microfalx.resource.ResourceUtils;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

import static net.microfalx.lang.ArgumentUtils.requireNonNull;

/**
 * Base class for all diagnostics.
 */
@Getter
@ToString(callSuper = true)
public abstract class Diagnostics extends NamedIdentityAware<String> {

    private static final long MAX_STORED_DIAGNOSTICS_SIZE = 1024 * 1024;
    private static final DateTimeFormatter FILE_NAME_TIMESTAMP_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");

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

    /**
     * The options used to collect the diagnostics.
     */
    private final Options options;

    @Getter(AccessLevel.NONE)
    transient DiagnosticsService diagnosticsService;

    public Diagnostics(String name, Options options) {
        requireNonNull(options);
        setId(IdGenerator.get().nextAsString());
        setName(name);
        this.options = options;
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
     * Returns the file name used to store the diagnostics data.
     * <p>
     * The file name is composed of the diagnostics type, the current timestamp and the diagnostics identifier,
     * like {@code heap_dump_20261111_143000_<id>.hprof}.
     *
     * @param extension the file extension, with or without the leading dot
     * @return a non-null instance
     */
    protected String getFileName(String extension) {
        requireNonNull(extension);
        if (!extension.startsWith(".")) extension = "." + extension;
        return getType().name().toLowerCase() + "_" + FILE_NAME_TIMESTAMP_FORMAT.format(LocalDateTime.now())
                + "_" + getId() + extension;
    }

    /**
     * Returns the (local) file where the diagnostics data should be stored.
     * <p>
     * The parent directory is created if it does not exist.
     *
     * @param directory the directory where the diagnostics are stored
     * @param extension the file extension, with or without the leading dot
     * @return a non-null instance
     */
    protected File getFile(Resource directory, String extension) {
        requireNonNull(directory);
        File file = ResourceUtils.toFile(directory.resolve(getFileName(extension), Resource.Type.FILE));
        File parent = file.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            throw new DiagnosticsException("Cannot create diagnostics directory: " + parent);
        }
        return file;
    }

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

    /**
     * Options used to collect diagnostics.
     * <p>
     * Each diagnostics type can extend this class (with {@link SuperBuilder}) to add its own options.
     */
    @Getter
    @ToString
    @SuperBuilder
    public static class Options {

        /**
         * Returns the default options.
         *
         * @return a non-null instance
         */
        public static Options create() {
            return builder().build();
        }
    }

    public enum Type {
        PROFILE,
        HEAP_DUMP,
        HISTOGRAM
    }
}
