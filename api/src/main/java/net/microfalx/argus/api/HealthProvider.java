package net.microfalx.argus.api;

/**
 * A provider for the health associated with a resource type.
 */
public interface HealthProvider {

    /**
     * Returns the last calculated score for a given resource type.
     *
     * @return a non-null instance
     */
    Health getHealth(Resource.Type type);
}
