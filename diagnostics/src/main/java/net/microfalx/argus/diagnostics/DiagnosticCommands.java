package net.microfalx.argus.diagnostics;

import javax.management.JMException;
import javax.management.MBeanServer;
import javax.management.ObjectName;
import java.lang.management.ManagementFactory;

import static net.microfalx.lang.ArgumentUtils.requireNonNull;
import static net.microfalx.lang.ArgumentUtils.requireNotEmpty;
import static net.microfalx.lang.StringUtils.isEmpty;

/**
 * Executes HotSpot diagnostic commands (the commands available with {@code jcmd}) in the current JVM, through the
 * diagnostic command MBean ({@code com.sun.management:type=DiagnosticCommand}).
 * <p>
 * Each command is exported as an MBean operation, named after the command in camel case
 * ({@code VM.info} becomes {@code vmInfo}, {@code GC.class_histogram} becomes {@code gcClassHistogram}), which
 * receives the command arguments as a {@code String[]}.
 */
final class DiagnosticCommands {

    static final String MBEAN = "com.sun.management:type=DiagnosticCommand";

    private DiagnosticCommands() {
    }

    /**
     * Executes a diagnostic command and returns its output.
     *
     * @param operation the MBean operation (like {@code vmInfo})
     * @param arguments the command arguments
     * @return the output of the command, never empty
     * @throws DiagnosticsException if the command cannot be executed or it returns an empty output
     */
    static String execute(String operation, String... arguments) {
        requireNotEmpty(operation);
        requireNonNull(arguments);
        MBeanServer server = ManagementFactory.getPlatformMBeanServer();
        Object result;
        try {
            result = server.invoke(new ObjectName(MBEAN), operation, new Object[]{arguments},
                    new String[]{String[].class.getName()});
        } catch (JMException e) {
            throw new DiagnosticsException("Failed to execute diagnostic command '" + operation + "' with " + MBEAN, e);
        }
        String output = result != null ? result.toString() : null;
        if (isEmpty(output)) {
            throw new DiagnosticsException("Diagnostic command '" + operation + "' returned an empty output");
        }
        return output;
    }
}
