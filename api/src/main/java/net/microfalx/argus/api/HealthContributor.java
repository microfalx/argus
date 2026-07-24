package net.microfalx.argus.api;

/**
 * A listener used to collect diagnostics information from services.
 */
public interface HealthContributor {

    /**
     * Invoked when the process (service replica) needs to be assessed.
     *
     * @param health the assessment to update
     */
    void update(Health health);
}
