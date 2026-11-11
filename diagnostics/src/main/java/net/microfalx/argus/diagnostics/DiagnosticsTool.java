package net.microfalx.argus.diagnostics;

import com.google.common.base.MoreObjects;
import net.microfalx.jvm.process.ProcessLauncher;
import net.microfalx.lang.JvmUtils;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.time.Duration;

import static net.microfalx.lang.ArgumentUtils.requireNonNull;
import static net.microfalx.lang.ArgumentUtils.requireNotEmpty;

/**
 * An external tool which can be used to launch diagnostics.
 * <p>
 * The executable is resolved against the bin directory ({@link JvmUtils#getBinDirectory()}, usually {@code ~/bin}).
 * A new process is launched for each {@link #execute(String...)} call.
 */
public class DiagnosticsTool {

    private static final Duration DEFAULT_TIMEOUT = Duration.ofMinutes(5);

    private final String executable;
    private Duration timeout = DEFAULT_TIMEOUT;

    public DiagnosticsTool(String executable) {
        requireNotEmpty(executable);
        this.executable = executable;
    }

    /**
     * Returns the (resolved) executable of the tool.
     *
     * @return a non-null instance
     */
    public File getExecutable() {
        return JvmUtils.getExecutable(executable);
    }

    /**
     * Returns whether the tool is installed.
     *
     * @return {@code true} if the executable exists, {@code false} otherwise
     */
    public boolean isAvailable() {
        File file = getExecutable();
        return file.isFile() && file.canExecute();
    }

    /**
     * Returns the maximum duration of an execution.
     *
     * @return a non-null instance
     */
    public Duration getTimeout() {
        return timeout;
    }

    /**
     * Changes the maximum duration of an execution.
     *
     * @param timeout the timeout
     * @return self
     */
    public DiagnosticsTool setTimeout(Duration timeout) {
        requireNonNull(timeout);
        this.timeout = timeout;
        return this;
    }

    /**
     * Executes the tool and returns its output (standard output and standard error, merged).
     *
     * @param arguments the arguments passed to the tool
     * @return the output of the tool
     * @throws DiagnosticsException if the tool is not installed, cannot be executed or exits with a non-zero code
     */
    public String execute(String... arguments) {
        requireNonNull(arguments);
        File file = getExecutable();
        if (!isAvailable()) throw new DiagnosticsException("Diagnostics tool '" + executable + "' is not available at " + file);
        ProcessLauncher launcher = ProcessLauncher.create(file).setExecutionTimeout(timeout);
        for (String argument : arguments) {
            launcher.addArgument(argument);
        }
        int exitCode;
        try {
            exitCode = launcher.run();
        } catch (RuntimeException e) {
            throw new DiagnosticsException("Failed to execute diagnostics tool " + launcher.toDescription(), e);
        }
        String output = readOutput(launcher.getLogFile());
        if (exitCode != 0) {
            throw new DiagnosticsException("Diagnostics tool '" + executable + "' failed with exit code " + exitCode
                    + ", output:\n" + output);
        }
        return output;
    }

    private String readOutput(File file) {
        try {
            return file.exists() ? Files.readString(file.toPath(), Charset.defaultCharset()) : "";
        } catch (IOException e) {
            throw new DiagnosticsException("Failed to read the output of diagnostics tool '" + executable + "' from " + file, e);
        } finally {
            try {
                Files.deleteIfExists(file.toPath());
            } catch (IOException e) {
                // ignore, the log file will be cleaned up with the rest of the logs
            }
        }
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                .add("executable", executable)
                .add("timeout", timeout)
                .toString();
    }
}
