package net.microfalx.argus.spring;

import net.microfalx.argus.api.Failure.Type;
import net.microfalx.jvm.ObjectSizeEstimator;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.AuthorizationServiceException;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.security.authentication.InternalAuthenticationServiceException;
import org.springframework.security.authentication.ProviderNotFoundException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.firewall.RequestRejectedException;

import static net.microfalx.argus.api.Failure.registerSubType;
import static net.microfalx.argus.api.Failure.registerType;

class InitializeSecurity {

    void initialize() {
        registerFailureTypes();
        ObjectSizeEstimator sizeEstimator = ObjectSizeEstimator.get();
        sizeEstimator.registerShallowSizeOfSubclass(AuthenticationManager.class, 500);
    }

    private void registerFailureTypes() {
        registerSubType(Type.AUTHENTICATION, AuthenticationException.class);
        registerType(Type.SERVICE_UNAVAILABLE, AuthenticationServiceException.class);
        registerType(Type.SERVICE_UNAVAILABLE, InternalAuthenticationServiceException.class);
        registerType(Type.CONFIGURATION, ProviderNotFoundException.class);

        registerSubType(Type.AUTHORIZATION, AccessDeniedException.class);
        registerType(Type.SERVICE_UNAVAILABLE, AuthorizationServiceException.class);

        registerType(Type.ILLEGAL_INPUT, RequestRejectedException.class);
    }
}
