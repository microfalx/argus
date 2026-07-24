package net.microfalx.argus.api;

import net.microfalx.lang.Service;

public interface HealthService extends Service {

    static HealthService getInstance() {
        return Service.load(HealthService.class);
    }
}
