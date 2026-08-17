package net.microfalx.argus.api;

import lombok.Getter;
import lombok.ToString;
import net.microfalx.lang.NamedIdentityAware;

import java.io.Serial;
import java.util.ArrayList;
import java.util.Collection;

import static java.util.Collections.emptyList;
import static net.microfalx.lang.ArgumentUtils.requireNonNull;

/**
 * A description of the site supporting an application (a collection of resources).
 */
@Getter
@ToString(callSuper = true)
public final class Site extends NamedIdentityAware<String> {

    @Serial private static final long serialVersionUID = -3283679665859555687L;

    /**
     * Holds the health of the resource.
     */
    private Health health;

    /**
     * A collection of instances of the resource. This collection is empty by default and can be populated.
     */
    private Collection<Resource> resources = emptyList();

    /**
     * Returns the health of the site.
     * <p>
     * If the health is not set, it is calculated based on the health of the resources.
     *
     * @return a non-null instance
     */
    public Health getHealth() {
        if (health != null) {
            return health;
        } else {
            return calculateHealth();
        }
    }

    /**
     * Returns a new resource with a different health.
     * <p>
     * If no health is set, one is built out of the resources' health.
     *
     * @param health the new health
     * @return a new instance
     */
    public Site withHealth(Health health) {
        Site copy = (Site) copy();
        copy.health = health.readOnly();
        return copy;
    }

    /**
     * Returns a new resource with an additional instance.
     *
     * @param instance the new instance
     * @return a non-null instance
     */
    public Site withInstance(Resource instance) {
        requireNonNull(instance);
        Site copy = (Site) copy();
        copy.resources = new ArrayList<>(resources);
        copy.resources.add(instance);
        return copy;
    }

    /**
     * Returns a new resource with the instances replaced.
     *
     * @param instances the new instances
     * @return a non-null instance
     */
    public Site withInstances(Collection<Resource> instances) {
        requireNonNull(instances);
        Site copy = (Site) copy();
        copy.resources = new ArrayList<>(instances);
        return copy;
    }

    private Health calculateHealth() {
        Health calculatedHealth = new Health();
        for (Resource resource : resources) {
            Health resourceHealth = resource.getHealth();
            Health.Group group = calculatedHealth.getGroup(resource.getName());
            group.update(resource.getGroup(), resourceHealth.getScore());
        }
        return health;
    }
}
