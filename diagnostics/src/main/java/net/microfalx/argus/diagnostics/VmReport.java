package net.microfalx.argus.diagnostics;

import com.sun.management.HotSpotDiagnosticMXBean;
import com.sun.management.VMOption;
import lombok.Getter;
import lombok.ToString;
import lombok.experimental.SuperBuilder;
import lombok.extern.slf4j.Slf4j;
import net.microfalx.lang.ExceptionUtils;
import net.microfalx.resource.Resource;

import java.io.File;
import java.lang.management.ManagementFactory;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

import static net.microfalx.lang.ArgumentUtils.requireNonNull;

/**
 * A report about the internals of a JVM.
 * <p>
 * The report is a text file with the following sections:
 * <ul>
 *     <li><b>VM Info</b>: the output of the {@code VM.info} diagnostic command, the same information written in a
 *     crash report ({@code hs_err_pid<pid>.log}): JVM version and flags, command line, environment, GC and heap
 *     layout, metaspace, code cache, compilation and GC events, loaded native libraries, OS, CPU and memory.</li>
 *     <li><b>Native Memory</b>: the output of the {@code VM.native_memory} diagnostic command, the memory used by
 *     the JVM, by area (heap, class metadata, threads, code, GC, compiler, symbols, etc). Native memory tracking
 *     has to be enabled when the JVM is started ({@code -XX:NativeMemoryTracking=summary|detail}); if it is not
 *     enabled, the section contains only a note.</li>
 * </ul>
 * The diagnostic commands are executed through the diagnostic command MBean (see {@link DiagnosticCommands}).
 */
@Slf4j
@ToString(callSuper = true)
public class VmReport extends Diagnostics {

    private static final String EXTENSION = "txt";

    private static final String VM_INFO_OPERATION = "vmInfo";
    private static final String NATIVE_MEMORY_OPERATION = "vmNativeMemory";
    private static final String NATIVE_MEMORY_TRACKING_OPTION = "NativeMemoryTracking";
    private static final String NATIVE_MEMORY_TRACKING_OFF = "off";

    private static final String SECTION_SEPARATOR = "=".repeat(80);

    public VmReport(String name) {
        this(name, Options.create());
    }

    public VmReport(String name, Options options) {
        super(name, options);
        options.validate();
    }

    @Override
    public Options getOptions() {
        return (Options) super.getOptions();
    }

    @Override
    protected Type getType() {
        return Type.VM_REPORT;
    }

    @Override
    protected void collect(Resource directory) throws Exception {
        String report = createReport();
        File file = getFile(directory, EXTENSION);
        Files.writeString(file.toPath(), report, StandardCharsets.UTF_8);
        updateResource(Resource.file(file));
    }

    /**
     * Creates the report.
     *
     * @return the report
     */
    String createReport() {
        StringBuilder builder = new StringBuilder(128 * 1024);
        builder.append("VM Report '").append(getName()).append("', created at ")
                .append(DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(ZonedDateTime.now()))
                .append(", PID ").append(ProcessHandle.current().pid()).append("\n\n");
        appendSection(builder, "VM Info", DiagnosticCommands.execute(VM_INFO_OPERATION));
        NativeMemory nativeMemory = getOptions().getNativeMemory();
        if (nativeMemory != NativeMemory.NONE) {
            appendSection(builder, "Native Memory", extractNativeMemory(nativeMemory));
        }
        return builder.toString();
    }

    /**
     * Extracts the native memory report.
     * <p>
     * A failure to extract the native memory does not fail the report, the cause is included in the report instead.
     *
     * @param nativeMemory the level of details requested
     * @return the native memory section
     */
    String extractNativeMemory(NativeMemory nativeMemory) {
        String tracking = getNativeMemoryTracking();
        if (tracking == null || NATIVE_MEMORY_TRACKING_OFF.equalsIgnoreCase(tracking)) {
            return "Native memory tracking is not enabled, start the JVM with -XX:NativeMemoryTracking=summary "
                    + "(about 5-10% overhead) or -XX:NativeMemoryTracking=detail (includes call sites, higher overhead)\n";
        }
        // detail is available only if the JVM tracks call sites
        if (nativeMemory == NativeMemory.DETAIL && !NativeMemory.DETAIL.getValue().equalsIgnoreCase(tracking)) {
            nativeMemory = NativeMemory.SUMMARY;
        }
        try {
            return DiagnosticCommands.execute(NATIVE_MEMORY_OPERATION, nativeMemory.getValue(),
                    "scale=" + getOptions().getScale().name());
        } catch (Exception e) {
            LOGGER.warn("Failed to extract native memory for VM report '{}', root cause: {}", getName(),
                    ExceptionUtils.getRootCauseDescription(e));
            return "Failed to extract native memory, root cause: " + ExceptionUtils.getRootCauseDescription(e) + "\n";
        }
    }

    /**
     * Returns the native memory tracking level of the current JVM ({@code off}, {@code summary} or {@code detail}).
     *
     * @return the level, null if not available
     */
    static String getNativeMemoryTracking() {
        try {
            HotSpotDiagnosticMXBean bean = ManagementFactory.getPlatformMXBean(HotSpotDiagnosticMXBean.class);
            if (bean == null) return null;
            VMOption option = bean.getVMOption(NATIVE_MEMORY_TRACKING_OPTION);
            return option != null ? option.getValue() : null;
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static void appendSection(StringBuilder builder, String title, String content) {
        builder.append(SECTION_SEPARATOR).append('\n').append(title).append('\n')
                .append(SECTION_SEPARATOR).append("\n\n").append(content);
        if (!content.endsWith("\n")) builder.append('\n');
        builder.append('\n');
    }

    /**
     * Options for VM reports.
     */
    @Getter
    @ToString(callSuper = true)
    @SuperBuilder
    public static class Options extends Diagnostics.Options {

        /**
         * The level of details for the native memory section.
         */
        @lombok.Builder.Default
        private final NativeMemory nativeMemory = NativeMemory.SUMMARY;

        /**
         * The unit used to report native memory.
         */
        @lombok.Builder.Default
        private final Scale scale = Scale.MB;

        /**
         * Returns the default options.
         *
         * @return a non-null instance
         */
        public static Options create() {
            return builder().build();
        }

        void validate() {
            requireNonNull(nativeMemory);
            requireNonNull(scale);
        }
    }

    /**
     * The level of details for the native memory section.
     */
    @Getter
    public enum NativeMemory {

        /**
         * The native memory section is not included.
         */
        NONE("none"),

        /**
         * Memory usage by area.
         */
        SUMMARY("summary"),

        /**
         * Memory usage by area, virtual memory map and allocation call sites; requires
         * {@code -XX:NativeMemoryTracking=detail}, falls back to {@link #SUMMARY} otherwise.
         */
        DETAIL("detail");

        private final String value;

        NativeMemory(String value) {
            this.value = value;
        }
    }

    /**
     * The units used to report memory.
     */
    public enum Scale {
        KB,
        MB,
        GB
    }
}
