package net.microfalx.argus.spring;

import lombok.extern.slf4j.Slf4j;
import net.microfalx.argus.api.Failure.Type;
import net.microfalx.jvm.ObjectSizeEstimator;
import org.hibernate.collection.spi.PersistentCollection;
import org.hibernate.proxy.HibernateProxy;
import org.springframework.boot.autoconfigure.orm.jpa.JpaProperties;
import org.springframework.dao.*;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.core.RepositoryCreationException;
import org.springframework.data.repository.core.support.IncompleteRepositoryCompositionException;
import org.springframework.data.repository.query.QueryCreationException;
import org.springframework.jdbc.CannotGetJdbcConnectionException;
import org.springframework.jdbc.datasource.lookup.DataSourceLookupFailureException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.orm.ObjectRetrievalFailureException;
import org.springframework.orm.jpa.JpaObjectRetrievalFailureException;
import org.springframework.orm.jpa.JpaOptimisticLockingFailureException;
import org.springframework.transaction.*;

import static net.microfalx.argus.api.Failure.registerSubType;
import static net.microfalx.argus.api.Failure.registerType;
import static net.microfalx.lang.ExceptionUtils.getRootCauseDescription;

@Slf4j
public class InitializeJpa {

    void initialize() {
        registerFailureTypes();
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

    private void registerFailureTypes() {
        registerType(Type.CONNECTIVITY, CannotGetJdbcConnectionException.class);

        registerType(Type.TIMED_OUT, QueryTimeoutException.class);
        registerType(Type.TIMED_OUT, TransactionTimedOutException.class);

        registerType(Type.RESOURCE_UNAVAILABLE, DataAccessResourceFailureException.class);

        registerType(Type.RESOURCE_BUSY, PessimisticLockingFailureException.class);
        registerType(Type.RESOURCE_BUSY, CannotAcquireLockException.class);

        registerType(Type.RESOURCE_NOT_FOUND, EmptyResultDataAccessException.class);
        registerType(Type.RESOURCE_NOT_FOUND, ObjectRetrievalFailureException.class);
        registerType(Type.RESOURCE_NOT_FOUND, JpaObjectRetrievalFailureException.class);

        registerType(Type.AUTHORIZATION, PermissionDeniedDataAccessException.class);

        registerType(Type.CONFLICT, ConcurrencyFailureException.class);
        registerType(Type.CONFLICT, OptimisticLockingFailureException.class);
        registerType(Type.CONFLICT, ObjectOptimisticLockingFailureException.class);
        registerType(Type.CONFLICT, JpaOptimisticLockingFailureException.class);
        registerType(Type.CONFLICT, DuplicateKeyException.class);

        registerType(Type.ILLEGAL_INPUT, DataIntegrityViolationException.class);
        registerType(Type.ILLEGAL_INPUT, TypeMismatchDataAccessException.class);

        registerType(Type.ABORT, UnexpectedRollbackException.class);

        registerType(Type.TRANSIENT_DATA_ACCESS, RecoverableDataAccessException.class);
        registerType(Type.TRANSIENT_DATA_ACCESS, CannotCreateTransactionException.class);
        registerSubType(Type.TRANSIENT_DATA_ACCESS, TransientDataAccessException.class);

        registerType(Type.NON_TRANSIENT_DATA_ACCESS, TransactionSystemException.class);
        registerSubType(Type.NON_TRANSIENT_DATA_ACCESS, NonTransientDataAccessException.class);

        registerType(Type.CONFIGURATION, InvalidIsolationLevelException.class);
        registerType(Type.CONFIGURATION, InvalidTimeoutException.class);
        registerType(Type.CONFIGURATION, NestedTransactionNotSupportedException.class);
        registerType(Type.CONFIGURATION, TransactionSuspensionNotSupportedException.class);
        registerType(Type.CONFIGURATION, DataSourceLookupFailureException.class);
        registerType(Type.CONFIGURATION, RepositoryCreationException.class);
        registerType(Type.CONFIGURATION, QueryCreationException.class);
        registerType(Type.CONFIGURATION, IncompleteRepositoryCompositionException.class);
    }
}
