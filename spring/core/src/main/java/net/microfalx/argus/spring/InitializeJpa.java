package net.microfalx.argus.spring;

import lombok.extern.slf4j.Slf4j;
import net.microfalx.jvm.ObjectSizeEstimator;
import org.hibernate.collection.spi.PersistentCollection;
import org.hibernate.proxy.HibernateProxy;
import org.springframework.boot.autoconfigure.orm.jpa.JpaProperties;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.TransactionManager;

import static net.microfalx.lang.ExceptionUtils.getRootCauseDescription;

@Slf4j
public class InitializeJpa {

    void initialize() {
        ObjectSizeEstimator sizeEstimator = ObjectSizeEstimator.get();
        sizeEstimator.registerShallowSize(JpaProperties.class, 200);
        sizeEstimator.registerShallowSizeOfSubclass(JpaRepository.class, 100);
        sizeEstimator.registerShallowSizeOfSubclass(TransactionManager.class, 100);

        try {
            initializeHibernate();
        } catch (Exception e) {
            LOGGER.info("Failed to initialize Data, root cause: {}, skipping", getRootCauseDescription(e));
        }
    }

    void initializeHibernate() {
        ObjectSizeEstimator sizeEstimator = ObjectSizeEstimator.get();
        sizeEstimator.registerShallowSize(PersistentCollection.class, 100);
        sizeEstimator.registerShallowSizeOfSubclass(HibernateProxy.class, 24);
    }
}
