package net.microfalx.argus.api;

import lombok.Getter;
import lombok.ToString;
import net.microfalx.lang.NamedIdentityAware;
import net.microfalx.lang.StringUtils;

import java.io.Serial;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Objects;

import static java.util.Collections.emptyList;
import static net.microfalx.lang.ArgumentUtils.requireNonNull;
import static net.microfalx.lang.ArgumentUtils.requireNotEmpty;
import static net.microfalx.lang.StringUtils.capitalizeWords;

/**
 * A description of a resource supporting an application.
 */
@Getter
@ToString(callSuper = true)
@net.microfalx.lang.annotation.Version
public final class Resource extends NamedIdentityAware<String> implements Serializable {

    @Serial private static final long serialVersionUID = -8109456950494304957L;
    /**
     * Holds the aggregation group. This is an optional field and can be used to group resources together.
     */
    private String group;

    /**
     * The type of the resource.
     */
    private final Type type;

    /**
     * Holds the health of the resource.
     */
    private Health health;

    /**
     * A collection of instances of the resource. This collection is empty by default and can be populated.
     */
    private Collection<Resource> instances = emptyList();

    public static Resource create(Type type, String id) {
        requireNotEmpty(type);
        requireNotEmpty(id);
        return new Resource(id, capitalizeWords(id), type);
    }

    public Resource() {
        this("default", "Default", Type.OTHER);
    }

    Resource(String id, String name, Type type) {
        requireNotEmpty(id);
        requireNotEmpty(name);
        requireNotEmpty(type);
        this.setId(StringUtils.toIdentifier(type.name() + "_" + id));
        this.setName(name);
        this.type = type;
    }

    /**
     * Returns a new resource with a different group (name).
     *
     * @param group the new group
     * @return a new instance
     */
    public Resource withGroup(String group) {
        requireNonNull(group);
        Resource copy = (Resource) copy();
        copy.group = group;
        return copy;
    }

    /**
     * Returns a new resource with a different health.
     *
     * @param health the new health
     * @return a new instance
     */
    public Resource withHealth(Health health) {
        Resource copy = (Resource) copy();
        copy.health = health.readOnly();
        return copy;
    }

    /**
     * Returns a new resource with an additional instance.
     *
     * @param instance the new instance
     * @return a non-null instance
     */
    public Resource withInstance(Resource instance) {
        requireNonNull(instances);
        Resource copy = (Resource) copy();
        copy.instances = new ArrayList<>(instances);
        copy.instances.add(instance);
        copy.updateGroup();
        return copy;
    }

    /**
     * Returns a new resource with the instances replaced.
     *
     * @param instances the new instances
     * @return a non-null instance
     */
    public Resource withInstances(Collection<Resource> instances) {
        requireNonNull(instances);
        Resource copy = (Resource) copy();
        copy.instances = new ArrayList<>(instances);
        copy.updateGroup();
        return copy;
    }

    private void updateGroup() {
        if (StringUtils.isNotEmpty(group)) return;
        group = instances.stream().map(Resource::getGroup)
                .filter(StringUtils::isNotEmpty)
                .findFirst().orElse(null);
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        if (!super.equals(o)) return false;
        Resource resource = (Resource) o;
        return Objects.equals(getId(), resource.getId())
                && getType() == resource.getType();
    }

    @Override
    public int hashCode() {
        return Objects.hash(getId(), getType());
    }

    public enum Type {
        SERVICE,
        SERVER,
        DATABASE,
        CACHE,
        OTHER
    }

}
