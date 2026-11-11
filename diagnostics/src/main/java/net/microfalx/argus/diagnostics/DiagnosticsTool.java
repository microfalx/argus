package net.microfalx.argus.diagnostics;

import com.google.common.base.MoreObjects;
import net.microfalx.jvm.process.ProcessLauncher;
import net.microfalx.lang.JvmUtils;

/**
 * An external tool which can be used to launch diagnostics.
 */
public class DiagnosticsTool {

    private final ProcessLauncher launcher;

    public DiagnosticsTool(String executable) {
        launcher = ProcessLauncher.create(JvmUtils.getExecutable(executable));
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                .add("launcher", launcher)
                .toString();
    }
}
