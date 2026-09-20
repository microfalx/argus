package net.microfalx.argus.spring;

import net.microfalx.jvm.ObjectSizeEstimator;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.core.support.RepositoryComposition;
import org.springframework.data.util.Lazy;
import org.springframework.data.util.TypeInformation;

class InitializeData {

    void initialize() {
        ObjectSizeEstimator sizeEstimator = ObjectSizeEstimator.get();
        sizeEstimator.registerShallowSize(Lazy.class, 32);
        sizeEstimator.registerShallowSize(RepositoryComposition.class, 100);
        sizeEstimator.registerShallowSizeOfSubclass(Repository.class, 100);
        sizeEstimator.registerShallowSizeOfSubclass(TypeInformation.class, 100);
    }
}
