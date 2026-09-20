package net.microfalx.argus.spring;

import net.microfalx.jvm.ObjectSizeEstimator;
import org.springframework.security.authentication.AuthenticationManager;

class InitializeSecurity {

    void initialize() {
        ObjectSizeEstimator sizeEstimator = ObjectSizeEstimator.get();
        sizeEstimator.registerShallowSizeOfSubclass(AuthenticationManager.class, 500);
    }
}
