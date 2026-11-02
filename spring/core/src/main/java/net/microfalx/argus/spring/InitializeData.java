package net.microfalx.argus.spring;

import net.microfalx.argus.api.Failure.Type;
import net.microfalx.jvm.ObjectSizeEstimator;
import org.springframework.data.mapping.MappingException;
import org.springframework.data.mapping.PropertyReferenceException;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.core.support.RepositoryComposition;
import org.springframework.data.repository.query.ParameterOutOfBoundsException;
import org.springframework.data.repository.support.QueryMethodParameterConversionException;
import org.springframework.data.util.Lazy;
import org.springframework.data.util.TypeInformation;

import static net.microfalx.argus.api.Failure.registerType;

class InitializeData {

    void initialize() {
        registerFailureTypes();
        ObjectSizeEstimator sizeEstimator = ObjectSizeEstimator.get();
        sizeEstimator.registerShallowSize(Lazy.class, 32);
        sizeEstimator.registerShallowSize(RepositoryComposition.class, 100);
        sizeEstimator.registerShallowSizeOfSubclass(Repository.class, 100);
        sizeEstimator.registerShallowSizeOfSubclass(TypeInformation.class, 100);
    }

    private void registerFailureTypes() {
        registerType(Type.CONFIGURATION, MappingException.class);

        registerType(Type.ILLEGAL_INPUT, PropertyReferenceException.class);
        registerType(Type.ILLEGAL_INPUT, ParameterOutOfBoundsException.class);
        registerType(Type.ILLEGAL_INPUT, QueryMethodParameterConversionException.class);
    }
}
