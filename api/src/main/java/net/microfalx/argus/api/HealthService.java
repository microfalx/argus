package net.microfalx.argus.api;

import net.microfalx.lang.service.Service;

import java.util.Collection;

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
     * with {@link #registerContributor(HealthContributor)}.
     *
     * @return a non-null collection
     */
    Collection<HealthContributor> getContributors();

    /**
     * Returns registered thresholds.
     *
     * @return a non-null collection
     */
    Collection<Thresholds> getThresholds();

    /**
     * Registers a new contributor.
     *
     * @param contributor the contributor
     */
    void registerContributor(HealthContributor contributor);

    /**
     * Unregisters a new contributor.
     *
     * @param contributor the contributor
     */
    void unregisterContributor(HealthContributor contributor);
}
