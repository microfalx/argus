package net.microfalx.argus.api;

import lombok.Getter;
import lombok.ToString;
import net.microfalx.lang.NamedIdentityAware;
import net.microfalx.lang.StringUtils;

import static net.microfalx.lang.ArgumentUtils.requireNonNull;

/**
 * Identifies the service supporting an application (a collection of resources).
 */
@Getter
@ToString(callSuper = true)
public final class Service extends NamedIdentityAware<String> {

    private String slotId;
    private String instanceId;

    /**
     * Creates a new service with the given name. The identifier is generated from the name.
     *
     * @param name the name of the service
     * @return a non-null instance
     */
    public static Service create(String name) {
        requireNonNull(name);
        return create(name, StringUtils.toIdentifier(name));
    }

    /**
     * Creates a new service with the given name and identifier.
     *
     * @param name the name of the service
     * @param id   the identifier of the service
     * @return a non-null instance
     */
    public static Service create(String name, String id) {
        requireNonNull(name);
        requireNonNull(id);
        Service service = new Service();
        service.setId(id);
        service.setName(name);
        return service;
    }

    /**
     * Creates a new service with the given description.
     *
     * @param description the new description
     * @return a non-null instance
     */
    public Service withDescription(String description) {
        Service copy = (Service) copy();
        copy.setDescription(description);
        return copy;
    }

    /**
     * Creates a new service with the given instance identifier.
     *
     * @param instanceId the instance
     * @return a non-null instance
     */
    public Service withInstanceId(String instanceId) {
        requireNonNull(instanceId);
        Service copy = (Service) copy();
        copy.instanceId = instanceId;
        return copy;
    }

    /**
     * Creates a new service with the given slot identifier (replica's unique index).
     *
     * @param slotId the slot identifier
     * @return a non-null instance
     */
    public Service withSlotId(String slotId) {
        requireNonNull(slotId);
        Service copy = (Service) copy();
        copy.slotId = slotId;
        return copy;
    }
}
