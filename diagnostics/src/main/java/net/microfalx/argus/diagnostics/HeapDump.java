package net.microfalx.argus.diagnostics;

import com.sun.management.HotSpotDiagnosticMXBean;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.SuperBuilder;
import net.microfalx.lang.IOUtils;
import net.microfalx.resource.Resource;

import java.io.*;
import java.lang.management.ManagementFactory;
import java.nio.file.Files;
import java.util.zip.Deflater;
import java.util.zip.GZIPOutputStream;

/**
 * A memory dump of a JVM.
 * <p>
 * The dump is triggered through the HotSpot diagnostic MBean
 * ({@code com.sun.management:type=HotSpotDiagnostic}) and written in HPROF format.
 * <p>
 * The {@code GC.heap_dump} diagnostic command (used by {@code jcmd}) has more options (like parallel dumps
 * and native compression), but it is not exported over JMX, so it cannot be invoked from within the JVM.
 */
@ToString(callSuper = true)
public class HeapDump extends Diagnostics {

    private static final String EXTENSION = "hprof";

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
        if (options.isCompressed()) {
            File compressedFile = new File(file.getParentFile(), file.getName() + ".gz");
            try {
                compress(file, compressedFile, options.getCompressionLevel());
            } finally {
                Files.deleteIfExists(file.toPath());
            }
            file = compressedFile;
        }
        updateResource(Resource.file(file));
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
        }
    }

    private static class LeveledGZIPOutputStream extends GZIPOutputStream {

        private LeveledGZIPOutputStream(OutputStream out, int level) throws IOException {
            super(out, IOUtils.BUFFER_SIZE);
            def.setLevel(level);
        }
    }
}
