package net.microfalx.argus.diagnostics;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.SuperBuilder;
import net.microfalx.argus.api.Failure;
import net.microfalx.lang.IdGenerator;
import net.microfalx.lang.NamedIdentityAware;
import net.microfalx.resource.MimeType;
import net.microfalx.resource.Resource;
import net.microfalx.resource.ResourceUtils;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

import static net.microfalx.lang.ArgumentUtils.requireNonNull;
import static net.microfalx.lang.StringUtils.EMPTY_STRING;
import static net.microfalx.lang.TextUtils.isBinaryContent;

/**
 * Base class for all diagnostics.
 */
@Getter
@ToString(callSuper = true)
public abstract class Diagnostics extends NamedIdentityAware<String> {

    /**
     * The text returned by {@link #getHead(int)} when the resource contains binary content.
     */
    public static final String BINARY_CONTENT = "[Binary content]";

    private static final long MAX_STORED_DIAGNOSTICS_SIZE = 1024 * 1024;
    private static final int BINARY_DETECTION_SAMPLE_SIZE = 8192;
    private static final DateTimeFormatter FILE_NAME_TIMESTAMP_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");

    /**
     * The URI of the resource associated with this diagnostics. This can be a file, a URL or any other resource.
     */
    private URI uri;

    /**
     * The data of the resource associated with this diagnostics. This is used to store the diagnostics data in memory
     * if the size of the resource is less than {@link #MAX_STORED_DIAGNOSTICS_SIZE}.
     */
    @Getter(AccessLevel.NONE)
    private byte[] data;

    /**
     * The size (in bytes) of the resource associated with this diagnostics, captured when the resource was stored.
     * <p>
     * The size is kept even if the resource is not available anymore. A negative value means the size is not known
     * (the diagnostics was not collected, or it failed).
     */
    private long size = -1;

    /**
     * The MIME type of the resource associated with this diagnostics, which tells consumers how to handle the
     * data (text, HTML, JSON, binary, etc).
     * <p>
     * A null value means the type is not known (the diagnostics was not collected, or it failed).
     */
    @Getter(AccessLevel.NONE)
    private MimeType mimeType;

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

    /**
     * Used by serialization (Kryo) only; the state is restored from the serialized data.
     */
    protected Diagnostics() {
        this.options = null;
    }

    public Diagnostics(String name, Options options) {
        requireNonNull(options);
        setId(IdGenerator.get().nextAsString());
        setName(name);
        this.options = options;
    }

    /**
     * Changes the description of the diagnostics, usually the reason why the diagnostics was collected
     * (like the health issue which triggered it).
     * <p>
     * The description is stored in the field inherited from {@link NamedIdentityAware}, which is
     * serialized together with the rest of the state.
     *
     * @param description the description, can be null
     */
    void updateDescription(String description) {
        setDescription(description);
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
     * Returns whether the diagnostics is running.
     *
     * @return {@code true} if the diagnostics is running, {@code false} otherwise
     */
    public boolean isRunning() {
        return startTime != null && endTime == null;
    }

    /**
     * Returns the MIME type of the resource associated with this diagnostics.
     *
     * @return a non-null instance, {@link MimeType#APPLICATION_OCTET_STREAM} if the type is not known
     */
    public MimeType getMimeType() {
        return mimeType != null ? mimeType : MimeType.APPLICATION_OCTET_STREAM;
    }

    /**
     * Returns whether the resource associated with this diagnostics is text (plain text, HTML, JSON, etc).
     *
     * @return {@code true} if text, {@code false} if binary or the type is not known
     */
    public boolean isText() {
        return mimeType != null && mimeType.isText();
    }

    /**
     * Returns whether the resource associated with this diagnostics is HTML.
     *
     * @return {@code true} if HTML, {@code false} if not or the type is not known
     */
    public boolean isHtml() {
        return mimeType != null && mimeType.equals(MimeType.TEXT_HTML);
    }

    /**
     * Returns whether the resource associated with this diagnostics is binary (image, audio, video, etc).
     *
     * @return {@code true} if exists (has data collected), {@code false} otherwise
     */
    public boolean exists() {
        return data != null || uri != null;
    }

    /**
     * Returns the resource associated with this diagnostics.
     * <p>
     * The resource carries the MIME type of the diagnostics.
     *
     * @return a non-null instance
     */
    public Resource getResource() {
        if (data != null) return Resource.bytes(data).withMimeType(getMimeType());
        if (uri != null) return Resource.uri(uri).withMimeType(getMimeType());
        throw new DiagnosticsNotFoundException("Diagnostics resource is not available for " + getName());
    }

    /**
     * Returns the first N lines of the resource associated with this diagnostics, if the resource is text.
     * <p>
     * If the resource contains binary content (based on the MIME type, or on the content itself when the MIME type
     * is not known), {@link #BINARY_CONTENT} is returned instead.
     *
     * @param lineCount the maximum number of lines to return
     * @return a non-null instance
     * @throws IOException if an I/O error occurs
     */
    public String getHead(int lineCount) throws IOException {
        if (lineCount <= 0) return EMPTY_STRING;
        if (mimeType != null && mimeType.isBinary()) return BINARY_CONTENT;
        try (BufferedReader reader = new BufferedReader(getResource().getReader(), BINARY_DETECTION_SAMPLE_SIZE)) {
            reader.mark(BINARY_DETECTION_SAMPLE_SIZE);
            char[] buffer = new char[BINARY_DETECTION_SAMPLE_SIZE];
            int count = reader.read(buffer, 0, buffer.length);
            if (count <= 0) return EMPTY_STRING;
            if (isBinaryContent(new String(buffer, 0, count))) return BINARY_CONTENT;
            reader.reset();
            StringBuilder builder = new StringBuilder();
            String line;
            int lines = 0;
            while (lines < lineCount && (line = reader.readLine()) != null) {
                if (lines > 0) builder.append('\n');
                builder.append(line);
                lines++;
            }
            return builder.toString();
        }
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
            diagnosticsService.update(this);
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
     * <p>
     * The MIME type is taken from the resource: the one attached with {@link Resource#withMimeType(MimeType)},
     * or the detected one when none is attached.
     *
     * @param resource the resource
     * @throws IOException if an I/O error occurs
     */
    protected void updateResource(Resource resource) throws IOException {
        requireNonNull(resource);
        this.mimeType = MimeType.get(resource.getMimeType());
        this.uri = resource.toURI();
        this.size = resource.length();
        if (size <= MAX_STORED_DIAGNOSTICS_SIZE) {
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
    @NoArgsConstructor(access = AccessLevel.PROTECTED)
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
        HISTOGRAM,
        THREAD_DUMP,
        VM_REPORT,
        FLIGHT_RECORDING
    }
}
