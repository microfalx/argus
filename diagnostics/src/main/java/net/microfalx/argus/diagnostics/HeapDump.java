package net.microfalx.argus.diagnostics;

import com.sun.management.HotSpotDiagnosticMXBean;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.SuperBuilder;
import lombok.extern.slf4j.Slf4j;
import net.microfalx.lang.IOUtils;
import net.microfalx.resource.MimeType;
import net.microfalx.resource.Resource;

import java.io.*;
import java.lang.management.ManagementFactory;
import java.nio.file.Files;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.Deflater;
import java.util.zip.GZIPOutputStream;

import static net.microfalx.lang.ArgumentUtils.requireNonNull;
import static net.microfalx.lang.ExceptionUtils.getRootCauseDescription;

/**
 * A memory dump of a JVM.
 * <p>
 * The dump is triggered through the HotSpot diagnostic MBean
 * ({@code com.sun.management:type=HotSpotDiagnostic}) and written in HPROF format.
 * <p>
 * The {@code GC.heap_dump} diagnostic command (used by {@code jcmd}) has more options (like parallel dumps
 * and native compression), but it is not exported over JMX, so it cannot be invoked from within the JVM.
 * <p>
 * Since the dump is binary, a text summary (heap content, top classes by shallow size, largest instances) is
 * extracted with {@code hprof} (resolved against the bin directory, see {@link DiagnosticsTool}), if installed:
 * <pre>
 * hprof --top 20 heap_dump.hprof
 * </pre>
 */
@Slf4j
@ToString(callSuper = true)
public class HeapDump extends Diagnostics {

    private static final String EXTENSION = "hprof";
    private static final String HPROF = "hprof";

    /**
     * The maximum time given to {@code hprof} to parse the dump (large dumps can take minutes).
     */
    private static final Duration SUMMARY_TIMEOUT = Duration.ofMinutes(15);

    /**
     * Used by serialization (Kryo) only.
     */
    private HeapDump() {
    }

    public HeapDump(String name) {
        this(name, Options.create());
    }

    public HeapDump(String name, Options options) {
        super(name, options);
        options.validate();
    }

    @Override
    public Options getOptions() {
        return (Options) super.getOptions();
    }

    @Override
    protected Type getType() {
        return Type.HEAP_DUMP;
    }

    @Override
    protected void collect(Resource directory) throws Exception {
        Options options = getOptions();
        File file = getFile(directory, EXTENSION);
        dump(file, options.isLive());
        // the summary tool cannot read compressed dumps, so the summary is extracted before compression
        if (options.isSummary()) summarize(file);
        if (options.isCompressed()) {
            File compressedFile = new File(file.getParentFile(), file.getName() + ".gz");
            try {
                compress(file, compressedFile, options.getCompressionLevel());
            } finally {
                Files.deleteIfExists(file.toPath());
            }
            file = compressedFile;
        }
        updateResource(Resource.file(file).withMimeType(MimeType.APPLICATION_OCTET_STREAM));
    }

    private void dump(File file, boolean live) throws IOException {
        // the MBean refuses to overwrite an existing file
        Files.deleteIfExists(file.toPath());
        try {
            getHotSpotBean().dumpHeap(file.getAbsolutePath(), live);
        } catch (IOException | RuntimeException e) {
            throw new DiagnosticsException("Failed to create heap dump '" + getName() + "' in " + file, e);
        }
    }

    /**
     * Extracts a summary of the heap dump with {@code hprof} and attaches it to this diagnostics.
     * <p>
     * The summary is optional: if the tool is not installed or it fails, the heap dump is still available
     * (without a summary).
     *
     * @param file the (uncompressed) heap dump
     */
    private void summarize(File file) {
        DiagnosticsTool tool = new DiagnosticsTool(HPROF).setTimeout(SUMMARY_TIMEOUT);
        if (!tool.isAvailable()) {
            LOGGER.debug("Heap dump summary tool is not available at {}, skip summary for '{}'", tool.getExecutable(), getName());
            return;
        }
        try {
            updateSummary(tool.execute(getSummaryArguments(file).toArray(String[]::new)));
        } catch (Exception e) {
            LOGGER.warn("Failed to extract summary for heap dump '{}' from {}, root cause: {}", getName(), file,
                    getRootCauseDescription(e));
        }
    }

    /**
     * Returns the arguments passed to {@code hprof} to extract the summary.
     *
     * @param file the (uncompressed) heap dump
     * @return a non-null instance
     */
    List<String> getSummaryArguments(File file) {
        requireNonNull(file);
        List<String> arguments = new ArrayList<>();
        arguments.add("--top");
        arguments.add(Integer.toString(getOptions().getSummaryTop()));
        arguments.add(file.getAbsolutePath());
        return arguments;
    }

    private void compress(File source, File target, int level) throws IOException {
        try (InputStream inputStream = new BufferedInputStream(new FileInputStream(source), IOUtils.BUFFER_SIZE);
             OutputStream outputStream = new LeveledGZIPOutputStream(new FileOutputStream(target), level)) {
            inputStream.transferTo(outputStream);
        } catch (IOException e) {
            Files.deleteIfExists(target.toPath());
            throw new DiagnosticsException("Failed to compress heap dump '" + getName() + "' to " + target, e);
        }
    }

    private static HotSpotDiagnosticMXBean getHotSpotBean() {
        HotSpotDiagnosticMXBean bean = ManagementFactory.getPlatformMXBean(HotSpotDiagnosticMXBean.class);
        if (bean == null) {
            throw new DiagnosticsException("HotSpot diagnostic MBean is not available, heap dumps are not supported");
        }
        return bean;
    }

    /**
     * Options for heap dumps.
     */
    @Getter
    @ToString(callSuper = true)
    @SuperBuilder
    @NoArgsConstructor(access = AccessLevel.PRIVATE, force = true)
    public static class Options extends Diagnostics.Options {

        /**
         * Indicates whether only live (reachable) objects are dumped. A full GC is executed before the dump
         * when this is {@code true}; when {@code false}, unreachable objects are dumped too (no GC).
         */
        @lombok.Builder.Default
        private final boolean live = true;

        /**
         * The GZIP compression level, between 1 (fastest, recommended) and 9 (strongest); 0 disables compression.
         * <p>
         * The heap dump is compressed after it is written (the JVM cannot compress it in-process), so
         * temporary disk space for the uncompressed dump is still required.
         */
        @lombok.Builder.Default
        private final int compressionLevel = 1;

        /**
         * Indicates whether a summary (heap content, top classes by shallow size, largest instances) is extracted
         * from the heap dump with {@code hprof}, if the tool is installed.
         */
        @lombok.Builder.Default
        private final boolean summary = true;

        /**
         * The number of entries (classes, instances) reported in each section of the summary.
         */
        @lombok.Builder.Default
        private final int summaryTop = 200;

        /**
         * Returns the default options.
         *
         * @return a non-null instance
         */
        public static Options create() {
            return builder().build();
        }

        /**
         * Returns whether the heap dump is compressed.
         *
         * @return {@code true} if compressed, {@code false} otherwise
         */
        public boolean isCompressed() {
            return compressionLevel > 0;
        }

        void validate() {
            if (compressionLevel < Deflater.NO_COMPRESSION || compressionLevel > Deflater.BEST_COMPRESSION) {
                throw new IllegalArgumentException("Compression level must be between 0 and 9, received " + compressionLevel);
            }
            if (summaryTop <= 0) {
                throw new IllegalArgumentException("Summary top must be positive, received " + summaryTop);
            }
        }
    }

    private static class LeveledGZIPOutputStream extends GZIPOutputStream {

        private LeveledGZIPOutputStream(OutputStream out, int level) throws IOException {
            super(out, IOUtils.BUFFER_SIZE);
            def.setLevel(level);
        }
    }
}
