package net.microfalx.argus.diagnostics;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.SuperBuilder;
import lombok.extern.slf4j.Slf4j;
import net.microfalx.lang.ExceptionUtils;
import net.microfalx.resource.Resource;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import static net.microfalx.lang.StringUtils.isEmpty;

/**
 * A histogram of in memory objects for a JVM.
 * <p>
 * The histogram is extracted with the {@code GC.class_histogram} diagnostic command, invoked through the
 * HotSpot diagnostic command MBean ({@code com.sun.management:type=DiagnosticCommand}). If the MBean is not
 * available (or it fails), the histogram is extracted with <a href="https://github.com/jattach/jattach">jattach</a>
 * ({@code jattach <pid> inspectheap}), which attaches to the current JVM from an external process.
 * <p>
 * The histogram is stored as text, in the same format produced by {@code jmap -histo} / {@code jcmd GC.class_histogram}.
 */
@Slf4j
@ToString(callSuper = true)
public class Histogram extends Diagnostics {

    private static final String EXTENSION = "txt";

    private static final String CLASS_HISTOGRAM_OPERATION = "gcClassHistogram";
    private static final String ALL_OBJECTS_ARGUMENT = "-all";

    private static final String JATTACH = "jattach";
    private static final String JATTACH_INSPECT_HEAP = "inspectheap";
    private static final String JATTACH_RESPONSE_CODE = "JVM response code";

    /**
     * Used by serialization (Kryo) only.
     */
    private Histogram() {
    }

    public Histogram(String name) {
        this(name, Options.create());
    }

    public Histogram(String name, Options options) {
        super(name, options);
    }

    @Override
    public Options getOptions() {
        return (Options) super.getOptions();
    }

    @Override
    protected Type getType() {
        return Type.HISTOGRAM;
    }

    @Override
    protected void collect(Resource directory) throws Exception {
        String histogram = extract(getOptions().isLive());
        File file = getFile(directory, EXTENSION);
        Files.writeString(file.toPath(), histogram, StandardCharsets.UTF_8);
        updateResource(Resource.file(file));
    }

    private String extract(boolean live) {
        try {
            return extractWithMBean(live);
        } catch (Exception e) {
            LOGGER.warn("Failed to extract histogram '{}' with the diagnostic command MBean, fallback to {}, root cause: {}",
                    getName(), JATTACH, ExceptionUtils.getRootCauseDescription(e));
            try {
                return extractWithTool(live);
            } catch (Exception te) {
                te.addSuppressed(e);
                throw te;
            }
        }
    }

    /**
     * Extracts the histogram with the {@code GC.class_histogram} diagnostic command, exported over JMX.
     *
     * @param live {@code true} to include only live objects (triggers a full GC), {@code false} to include all objects
     * @return the histogram
     */
    String extractWithMBean(boolean live) {
        String[] arguments = live ? new String[0] : new String[]{ALL_OBJECTS_ARGUMENT};
        return DiagnosticCommands.execute(CLASS_HISTOGRAM_OPERATION, arguments);
    }

    /**
     * Extracts the histogram with {@code jattach}, attached to the current JVM.
     *
     * @param live {@code true} to include only live objects (triggers a full GC), {@code false} to include all objects
     * @return the histogram
     */
    String extractWithTool(boolean live) {
        DiagnosticsTool tool = new DiagnosticsTool(JATTACH);
        String pid = Long.toString(ProcessHandle.current().pid());
        String output = live ? tool.execute(pid, JATTACH_INSPECT_HEAP)
                : tool.execute(pid, JATTACH_INSPECT_HEAP, ALL_OBJECTS_ARGUMENT);
        String histogram = stripToolHeader(output);
        if (isEmpty(histogram)) {
            throw new DiagnosticsException("Diagnostics tool '" + JATTACH + "' returned an empty histogram for '"
                    + getName() + "', output:\n" + output);
        }
        return histogram;
    }

    /**
     * Removes the lines printed by {@code jattach} before the response of the JVM
     * ({@code Connected to remote JVM}, {@code JVM response code = 0}).
     *
     * @param output the output of {@code jattach}
     * @return the response of the JVM
     */
    static String stripToolHeader(String output) {
        if (output == null) return null;
        int index = output.indexOf(JATTACH_RESPONSE_CODE);
        if (index < 0) return output;
        int endOfLine = output.indexOf('\n', index);
        return endOfLine < 0 ? "" : output.substring(endOfLine + 1);
    }

    /**
     * Options for histograms.
     */
    @Getter
    @ToString(callSuper = true)
    @SuperBuilder
    @NoArgsConstructor(access = AccessLevel.PRIVATE, force = true)
    public static class Options extends Diagnostics.Options {

        /**
         * Indicates whether only live (reachable) objects are included. A full GC is executed before the
         * histogram is extracted when this is {@code true}; when {@code false}, unreachable objects are included too (no GC).
         */
        @lombok.Builder.Default
        private final boolean live = true;

        /**
         * Returns the default options.
         *
         * @return a non-null instance
         */
        public static Options create() {
            return builder().build();
        }
    }
}
