package net.microfalx.argus.diagnostics;

import jdk.jfr.Configuration;
import jdk.jfr.Recording;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.SuperBuilder;
import lombok.extern.slf4j.Slf4j;
import net.microfalx.resource.MimeType;
import net.microfalx.resource.Resource;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.text.ParseException;
import java.time.Duration;

import static net.microfalx.lang.ArgumentUtils.requireNonNull;

/**
 * A Java Flight Recorder (JFR) recording of a JVM.
 * <p>
 * The recording is created in-process, with the JFR API ({@link Recording}), so it does not require any
 * external tool or native agent. It records the JVM for the requested duration, with one of the configurations
 * shipped with the JDK (see {@link Settings}): CPU samples, allocations, lock contention, GC, safepoints,
 * I/O, exceptions, class loading, compilation, etc.
 * <p>
 * The recording is written in JFR format and can be opened with JDK Mission Control (JMC), IntelliJ or
 * analyzed with the {@code jfr} tool ({@code jfr summary}, {@code jfr print}, {@code jfr view}).
 * <p>
 * The call blocks the current thread for the duration of the recording. If the thread is interrupted, the
 * recording is stopped early and the data recorded so far is kept.
 */
@Slf4j
@ToString(callSuper = true)
public class FlightRecording extends Diagnostics {

    private static final String EXTENSION = "jfr";

    /**
     * Used by serialization (Kryo) only.
     */
    private FlightRecording() {
    }

    public FlightRecording(String name) {
        this(name, Options.create());
    }

    public FlightRecording(String name, Options options) {
        super(name, options);
        options.validate();
    }

    @Override
    public Options getOptions() {
        return (Options) super.getOptions();
    }

    @Override
    protected Type getType() {
        return Type.FLIGHT_RECORDING;
    }

    @Override
    protected void collect(Resource directory) throws Exception {
        Options options = getOptions();
        File file = getFile(directory, EXTENSION);
        Files.deleteIfExists(file.toPath());
        LOGGER.info("Flight recording '{}' for {}s, settings '{}'", getName(), options.getDuration().toSeconds(),
                options.getSettings().getValue());
        record(file, options);
        if (!file.isFile() || file.length() == 0) {
            throw new DiagnosticsException("Flight recorder did not produce any output for '" + getName() + "' in " + file);
        }
        updateResource(Resource.file(file).withMimeType(MimeType.APPLICATION_OCTET_STREAM));
    }

    private void record(File file, Options options) throws IOException {
        Configuration configuration;
        try {
            configuration = Configuration.getConfiguration(options.getSettings().getValue());
        } catch (ParseException e) {
            throw new DiagnosticsException("Failed to load flight recorder settings '"
                    + options.getSettings().getValue() + "'", e);
        }
        try (Recording recording = new Recording(configuration)) {
            recording.setName("Argus - " + getName());
            recording.setToDisk(true);
            recording.setDumpOnExit(false);
            recording.start();
            try {
                Thread.sleep(options.getDuration());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                LOGGER.warn("Flight recording '{}' was interrupted, keep the data recorded so far", getName());
            }
            recording.stop();
            recording.dump(file.toPath());
        } catch (IllegalStateException e) {
            throw new DiagnosticsException("Failed to create flight recording '" + getName() + "' in " + file, e);
        }
    }

    /**
     * Options for flight recordings.
     */
    @Getter
    @ToString(callSuper = true)
    @SuperBuilder
    @NoArgsConstructor(access = AccessLevel.PRIVATE, force = true)
    public static class Options extends Diagnostics.Options {

        /**
         * The duration of the recording (at least one second).
         */
        @lombok.Builder.Default
        private final Duration duration = Duration.ofSeconds(60);

        /**
         * The settings (configuration) used by the recording.
         */
        @lombok.Builder.Default
        private final Settings settings = Settings.PROFILE;

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
            requireNonNull(settings);
            if (duration.toSeconds() < 1) {
                throw new IllegalArgumentException("Flight recording duration must be at least one second, received " + duration);
            }
        }
    }

    /**
     * The settings (configurations) shipped with the JDK.
     */
    @Getter
    public enum Settings {

        /**
         * Low overhead (about 1%), suitable for continuous recordings; samples CPU every 20ms, records only
         * events above a threshold (like locks held more than 20ms).
         */
        DEFAULT("default"),

        /**
         * Higher overhead (about 2%), suitable for a recording of limited duration; samples CPU every 10ms,
         * records allocations and events with lower thresholds.
         */
        PROFILE("profile");

        private final String value;

        Settings(String value) {
            this.value = value;
        }
    }
}
