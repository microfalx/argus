package net.microfalx.argus.core.contributor;

import net.microfalx.argus.api.Health;
import net.microfalx.argus.api.Resource;
import net.microfalx.argus.api.Thresholds;
import net.microfalx.lang.annotation.Provider;

import java.util.Collection;
import java.util.List;

@Provider
public class ServiceHealthContributor extends AbstractHealthContributor {

    @Override
    public String getName() {
        return "Services";
    }

    @Override
    public boolean supports(Resource.Type type) {
        return type == Resource.Type.SERVICE;
    }

    @Override
    public void update(Health health) {

    }

    @Override
    public Collection<Thresholds> getThresholds() {
        return List.of();
    }
}
