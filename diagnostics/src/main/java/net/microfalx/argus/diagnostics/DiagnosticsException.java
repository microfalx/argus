package net.microfalx.argus.diagnostics;

import net.microfalx.service.api.ServiceException;

/**
 * Base class for all diagnostics exceptions.
 */
public class DiagnosticsException extends ServiceException {

    public DiagnosticsException(String message) {
        super(message);
    }

    public DiagnosticsException(String message, Throwable cause) {
        super(message, cause);
    }
}
