package net.microfalx.argus.api;

import lombok.Getter;
import lombok.ToString;
import net.microfalx.lang.NamedIdentityAware;

import static net.microfalx.lang.ArgumentUtils.requireNotEmpty;

/**
 * A description of a resource supporting an application.
 */
@Getter
@ToString
public class Resource extends NamedIdentityAware<String> {

    private final Type type;

    Resource(String id, String name, Type type) {
        requireNotEmpty(id);
        requireNotEmpty(name);
        requireNotEmpty(type);
        this.setId(id);
        this.setName(name);
        this.type = type;
    }

    public enum Type {
        SERVICE,
        SERVER,
        DATABASE,
        CACHE,
        OTHER
    }
}
