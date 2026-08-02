package net.microfalx.argus.api;

import net.microfalx.lang.Nameable;
import net.microfalx.metrics.Batch;

import java.util.Collection;

/**
 * A listener used to collect diagnostics information from services.
 */
public interface HealthContributor extends Nameable {

    /**
     * Returns which resource type is contributor supporting.
     *
     * @param type the resource type
     * @return {@code true} if the contributor supports the given resource type, {@code false} otherwise
     */
    boolean supports(Resource.Type type);

    /**
     * Invoked when the process (service replica) needs to be assessed.
     *
     * @param health the assessment to update
     */
    void update(Health health);

    /**
     * Invoked when the process (service replica) needs to update its metrics.
     *
     * @param batch the batch where the metrics will be accumulated
     */
    default void update(Batch batch) {
        // default is empty, some contributors do not produce metrics
    }

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
