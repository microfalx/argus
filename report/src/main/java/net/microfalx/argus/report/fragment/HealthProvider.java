package net.microfalx.argus.report.fragment;

import net.microfalx.argus.api.Health;
import net.microfalx.argus.api.HealthService;
import net.microfalx.argus.api.Resource;
import net.microfalx.argus.report.AbstractFragmentProvider;
import net.microfalx.argus.report.Fragment;
import net.microfalx.argus.report.Template;
import net.microfalx.lang.annotation.Provider;

@Provider
public class HealthProvider extends AbstractFragmentProvider {

    @Override
    public Fragment create() {
        return Fragment.builder("Health").template("health")
                .icon("fa-solid fa-kit-medical")
                .order(20)
                .build();
    }

    @Override
    public void update(Template template) {
        doUpdate(template);
    }

    static void doUpdate(Template template) {
        HealthService healthService = HealthService.getInstance();
        template.addVariable("serviceResource", getResource(healthService, Resource.Type.SERVICE));
        template.addVariable("serverResource", getResource(healthService, Resource.Type.SERVER));
        template.addVariable("databaseResource", getResource(healthService, Resource.Type.DATABASE));
        template.addVariable("cacheResource", getResource(healthService, Resource.Type.CACHE));
        template.addVariable("brokerResource", getResource(healthService, Resource.Type.BROKER));
    }

    private static Resource getResource(HealthService healthService, Resource.Type type) {
        try {
            Resource resource = healthService.getResource(type);
            Health health = resource.getHealth();
            if (type == Resource.Type.SERVICE || type == Resource.Type.SERVER) {
                return resource;
            } else {
                return health.getGroups().isEmpty() ? null : resource;
            }
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
