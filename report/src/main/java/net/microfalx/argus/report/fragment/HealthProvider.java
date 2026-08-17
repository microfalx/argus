package net.microfalx.argus.report.fragment;

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
        template.addVariable("serviceResource", healthService.getResource(Resource.Type.SERVICE));
        template.addVariable("serverResource", healthService.getResource(Resource.Type.SERVER));
    }
}
