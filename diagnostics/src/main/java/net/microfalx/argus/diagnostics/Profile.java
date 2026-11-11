package net.microfalx.argus.diagnostics;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.SuperBuilder;
import lombok.extern.slf4j.Slf4j;
import net.microfalx.lang.JvmUtils;
import net.microfalx.resource.Resource;

import java.io.File;
import java.nio.file.Files;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static net.microfalx.lang.ArgumentUtils.requireNonNull;

/**
 * A (sampling) profile of a JVM, collected with <a href="https://github.com/async-profiler/async-profiler">async-profiler</a>.
 * <p>
 * The profiler is launched with {@code asprof} (resolved against the bin directory, see {@link DiagnosticsTool}),
 * which attaches to the current JVM, profiles it for the requested duration and writes the result to a file:
 * <pre>
 * asprof -d 60 -e cpu --all-user -o flamegraph -f profile.html --libpath ~/lib/profiler/libasyncProfiler.so &lt;pid&gt;
 * </pre>
 * The native library is resolved against the library directory ({@link JvmUtils#getLibraryDirectory()}, usually
 * {@code ~/lib/profiler/libasyncProfiler.so}); if it is missing, {@code asprof} falls back to its own lookup.
 * <p>
 * The call blocks the current thread for the duration of the profile.
 */
@Slf4j
@ToString(callSuper = true)
public class Profile extends Diagnostics {

    private static final String ASPROF = "asprof";
    private static final String LIBRARY_DIRECTORY = "profiler";
    private static final String LIBRARY_NAME = "libasyncProfiler.so";

    /**
     * Extra time given to the tool, on top of the profiling duration, to attach and write the output.
     */
    private static final Duration TOOL_TIMEOUT_MARGIN = Duration.ofMinutes(2);

    /**
     * Used by serialization (Kryo) only.
     */
    private Profile() {
    }

    public Profile(String name) {
        this(name, Options.create());
    }

    public Profile(String name, Options options) {
        super(name, options);
        options.validate();
    }

    @Override
    public Options getOptions() {
        return (Options) super.getOptions();
    }

    @Override
    protected Type getType() {
        return Type.PROFILE;
    }

    @Override
    protected void collect(Resource directory) throws Exception {
        Options options = getOptions();
        File file = getFile(directory, options.getFormat().getExtension());
        Files.deleteIfExists(file.toPath());
        DiagnosticsTool tool = new DiagnosticsTool(ASPROF).setTimeout(options.getDuration().plus(TOOL_TIMEOUT_MARGIN));
        LOGGER.info("Profile '{}' for {}s, event '{}', format '{}'", getName(), options.getDuration().toSeconds(),
                options.getEvent().getValue(), options.getFormat().getValue());
        String output = tool.execute(getArguments(file).toArray(String[]::new));
        if (!file.isFile() || file.length() == 0) {
            throw new DiagnosticsException("Profiler did not produce any output for '" + getName() + "' in " + file
                    + ", output:\n" + output);
        }
        updateResource(Resource.file(file));
    }

    /**
     * Returns the arguments passed to {@code asprof}.
     *
     * @param file the file where the profile is written
     * @return a non-null instance
     */
    List<String> getArguments(File file) {
        requireNonNull(file);
        Options options = getOptions();
        List<String> arguments = new ArrayList<>();
        arguments.add("-d");
        arguments.add(Long.toString(options.getDuration().toSeconds()));
        arguments.add("-e");
        arguments.add(options.getEvent().getValue());
        if (options.getInterval() > 0) {
            arguments.add("-i");
            arguments.add(Long.toString(options.getInterval()));
        }
        if (options.isAllUser()) arguments.add("--all-user");
        if (options.isThreads()) arguments.add("-t");
        arguments.add("-o");
        arguments.add(options.getFormat().getValue());
        arguments.add("-f");
        arguments.add(file.getAbsolutePath());
        File library = getLibrary();
        if (library.isFile()) {
            arguments.add("--libpath");
            arguments.add(library.getAbsolutePath());
        }
        arguments.add(Long.toString(ProcessHandle.current().pid()));
        return arguments;
    }

    /**
     * Returns the native library of the profiler.
     *
     * @return a non-null instance
     */
    static File getLibrary() {
        return new File(new File(JvmUtils.getLibraryDirectory(), LIBRARY_DIRECTORY), LIBRARY_NAME);
    }

    /**
     * Options for profiles.
     */
    @Getter
    @ToString(callSuper = true)
    @SuperBuilder
    @NoArgsConstructor(access = AccessLevel.PRIVATE, force = true)
    public static class Options extends Diagnostics.Options {

        /**
         * The duration of the profile (rounded down to seconds, at least one second).
         */
        @lombok.Builder.Default
        private final Duration duration = Duration.ofSeconds(60);

        /**
         * The profiled event.
         */
        @lombok.Builder.Default
        private final Event event = Event.CPU;

        /**
         * The format of the profile.
         */
        @lombok.Builder.Default
        private final Format format = Format.FLAMEGRAPH;

        /**
         * The sampling interval: nanoseconds for CPU/wall-clock events, bytes for allocations, nanoseconds of
         * contention for locks; 0 uses the profiler default.
         */
        private final long interval;

        /**
         * Indicates whether only user-mode events are collected (kernel frames are excluded).
         */
        @lombok.Builder.Default
        private final boolean allUser = true;

        /**
         * Indicates whether the profile is split by thread.
         */
        private final boolean threads;

        /**
         * Returns the default options.
         *
         * @return a non-null instance
         */
        public static Options create() {
            return builder().build();
        }

        void validate() {
            requireNonNull(duration);
            requireNonNull(event);
            requireNonNull(format);
            if (duration.toSeconds() < 1) {
                throw new IllegalArgumentException("Profile duration must be at least one second, received " + duration);
            }
            if (interval < 0) {
                throw new IllegalArgumentException("Profile interval cannot be negative, received " + interval);
            }
        }
    }

    /**
     * The events which can be profiled.
     */
    @Getter
    public enum Event {

        /**
         * CPU time (uses perf_events, falls back to timers when not available, like in containers).
         */
        CPU("cpu"),

        /**
         * Wall-clock time, samples all threads, regardless of their state (running, sleeping or blocked).
         */
        WALL("wall"),

        /**
         * CPU time sampled with {@code setitimer}, does not require perf_events.
         */
        ITIMER("itimer"),

        /**
         * Heap allocations.
         */
        ALLOC("alloc"),

        /**
         * Lock contention.
         */
        LOCK("lock");

        private final String value;

        Event(String value) {
            this.value = value;
        }
    }

    /**
     * The output formats of a profile.
     */
    @Getter
    public enum Format {

        /**
         * An interactive (HTML) flame graph.
         */
        FLAMEGRAPH("flamegraph", "html"),

        /**
         * Collapsed stacks (one stack per line, with the sample count), the input of most flame graph tools.
         */
        COLLAPSED("collapsed", "txt"),

        /**
         * The hottest methods (text).
         */
        FLAT("flat", "txt"),

        /**
         * The hottest stack traces (text).
         */
        TRACES("traces", "txt"),

        /**
         * A Java Flight Recorder file, which can be opened with JMC (or converted with {@code jfrconv}).
         */
        JFR("jfr", "jfr");

        private final String value;
        private final String extension;

        Format(String value, String extension) {
            this.value = value;
            this.extension = extension;
        }
    }
}
