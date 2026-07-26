package net.microfalx.argus.api;

import java.util.Collection;

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

    /**
     * Invoked periodically to recalculate threshold limits (for those thresholds which depend on historical values).
     */
    default void updateStats() {
        // empty by default
    }

    /**
     * Returns default thresholds used by this health contributor.
     *
     * @return a non-null instance
     */
    Collection<Thresholds> getThresholds();
}
