package net.microfalx.argus.spring.actuator;

import lombok.Getter;
import lombok.ToString;
import net.microfalx.argus.api.Resource;
import net.microfalx.lang.NamedIdentityAware;

import java.util.ArrayList;
import java.util.Collection;

@Getter
@ToString
public class ResourceMetadata extends NamedIdentityAware<String> {

    private Resource.Type type;
    private String group;
    private HealthMetadata health;
    private Collection<ResourceMetadata> instances = new ArrayList<>();

    public static ResourceMetadata of(Resource resource) {
        ResourceMetadata metadata = new ResourceMetadata();
        metadata.setId(resource.getId());
        metadata.setName(resource.getName());
        metadata.setDescription(resource.getDescription());
        metadata.type = resource.getType();
        metadata.group = resource.getGroup();
        metadata.instances = resource.getInstances().stream().map(ResourceMetadata::of).toList();
        if (resource.getHealth() != null) {
            metadata.health = HealthMetadata.of(resource.getHealth());
        }
        return metadata;
    }

}
