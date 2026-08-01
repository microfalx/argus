package net.microfalx.argus.api;

import net.microfalx.lang.service.Service;

import java.util.Collection;
import java.util.Set;

/**
 * A service responsible to calculate the health of the service.
 */
public interface HealthService extends Service {

    static HealthService getInstance() {
        return Service.lookup(HealthService.class);
    }

    /**
     * Returns the last calculated score.
     *
     * @return a non-null instance
     */
    Health getHealth();

    /**
     * Returns registered health contributors.
     * <p>
     * The complete list is based on contributors discovered from class path and those registered
     * with {@link #register(HealthContributor)}.
     *
     * @return a non-null collection
     */
    Collection<HealthContributor> getContributors();

    /**
     * Returns registered thresholds.
     *
     * @return a non-null collection
     */
    Set<Thresholds> getThresholds();

    /**
     * Registers a  threshold.
     *
     * @param threshold the threshold
     */
    void register(Thresholds threshold);

    /**
     * Registers a new contributor.
     *
     * @param contributor the contributor
     */
    void register(HealthContributor contributor);

    /**
     * Unregisters a new contributor.
     *
     * @param contributor the contributor
     */
    void unregister(HealthContributor contributor);
}
