package net.microfalx.argus.api;

import net.microfalx.metrics.SeriesStore;
import net.microfalx.metrics.statistics.TrendStatisticalSummary;

import java.util.Collection;
import java.util.Set;

/**
 * A service responsible to calculate the health of the service.
 */
public interface HealthService extends net.microfalx.lang.service.Service, HealthProvider {

    static HealthService getInstance() {
        return net.microfalx.lang.service.Service.lookup(HealthService.class);
    }

    /**
     * Returns the health settings.
     *
     * @return a non-null instance
     */
    HealthSettings getSettings();

    /**
     * Changes the health settings.
     *
     * @param settings the new settings
     */
    void setSettings(HealthSettings settings);

    /**
     * Returns the service metadata.
     *
     * @return a non-null instance
     */
    Service getService();

    /**
     * Changes the service metadata.
     *
     * @param service the service metadata
     */
    void setService(Service service);

    /**
     * Returns the metrics store for health related metrics.
     *
     * @return a non-null instance
     */
    SeriesStore getStore();

    /**
     * Returns the last calculated score for a given resource type.
     * <p>
     * The health is calculated based on the registered contributors and thresholds.
     *
     * @return a non-null instance
     */
    Health getHealth(Resource.Type type);

    /**
     * Returns the resource for a given type.
     * <p>
     * The resource has the health and health trend calculated based on the registered
     * contributors and thresholds.
     *
     * @param type the type
     * @return a non-null instance
     */
    Resource getResource(Resource.Type type);

    /**
     * Returns the last calculated health for a given health type.
     *
     * @return a non-null instance
     */
    Health getHealth(Health.Type type);

    /**
     * Returns the summary tracking a given last calculated metric for a given service.
     *
     * @return a non-null instance
     */
    TrendStatisticalSummary getTrend(net.microfalx.lang.service.Service service, net.microfalx.lang.service.Service.Metric metric);

    /**
     * Returns the resources of a given type.
     *
     * @return a non-null instance
     */
    Collection<Resource> getResources(Resource.Type type);

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

    /**
     * Updates the health status of all resources.
     */
    void update();
}
