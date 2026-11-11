package net.microfalx.argus.diagnostics;

/**
 * An exception raised when a diagnostics is not found.
 */
public class DiagnosticsNotFoundException extends DiagnosticsException {

    public DiagnosticsNotFoundException(String message) {
        super(message);
    }
}
