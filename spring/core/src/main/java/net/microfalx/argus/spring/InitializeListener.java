package net.microfalx.argus.spring;

import lombok.extern.slf4j.Slf4j;
import net.microfalx.argus.api.Failure;
import net.microfalx.argus.api.Failure.Type;
import net.microfalx.jvm.ObjectSizeEstimator;
import org.springframework.beans.*;
import org.springframework.beans.factory.*;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.context.config.ConfigDataResourceNotFoundException;
import org.springframework.boot.context.config.InactiveConfigDataAccessException;
import org.springframework.boot.context.event.ApplicationStartedEvent;
import org.springframework.boot.context.event.ApplicationStartingEvent;
import org.springframework.boot.context.properties.bind.UnboundConfigurationPropertiesException;
import org.springframework.boot.context.properties.bind.validation.BindValidationException;
import org.springframework.boot.context.properties.source.InvalidConfigurationPropertyNameException;
import org.springframework.boot.context.properties.source.InvalidConfigurationPropertyValueException;
import org.springframework.boot.context.properties.source.MutuallyExclusiveConfigurationPropertiesException;
import org.springframework.boot.ssl.NoSuchSslBundleException;
import org.springframework.boot.web.server.PortInUseException;
import org.springframework.context.*;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.codec.DecodingException;
import org.springframework.core.codec.EncodingException;
import org.springframework.core.convert.ConversionFailedException;
import org.springframework.core.convert.ConversionService;
import org.springframework.core.convert.ConverterNotFoundException;
import org.springframework.core.convert.converter.GenericConverter;
import org.springframework.core.env.Environment;
import org.springframework.core.io.ResourceLoader;
import org.springframework.core.io.buffer.DataBufferLimitException;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.util.InvalidMimeTypeException;
import org.springframework.util.PlaceholderResolutionException;
import org.springframework.validation.Validator;
import org.springframework.validation.method.MethodValidationException;

import static net.microfalx.argus.api.Failure.registerType;
import static net.microfalx.lang.ExceptionUtils.getRootCauseDescription;

/**
 * A listener which hooks into the Spring Boot application and integrates with the Argus framework.
 */
@Slf4j
public class InitializeListener implements ApplicationListener<ApplicationEvent>, Ordered {

    private final InitializeService initializeService = new InitializeService();

    @Override
    public void onApplicationEvent(ApplicationEvent event) {
        if (event instanceof ApplicationStartingEvent startingEvent) {
            onApplicationStarting(startingEvent);
        } else if (event instanceof ApplicationStartedEvent startedEvent) {
            onApplicationStartup(startedEvent);
        }
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }

    private void onApplicationStarting(ApplicationStartingEvent event) {
        registerFailureTypes();
        registerObjectSizes();
        initializeData();
        initializeSecurity();
        initializeJpa();
        initializeWeb();
    }

    private void registerFailureTypes() {
        Failure.registerPackageToIgnore("org.springframework");

        registerType(Type.CONFIGURATION, NoSuchBeanDefinitionException.class);
        registerType(Type.CONFIGURATION, NoUniqueBeanDefinitionException.class);
        registerType(Type.CONFIGURATION, BeanCurrentlyInCreationException.class);
        registerType(Type.CONFIGURATION, BeanDefinitionStoreException.class);
        registerType(Type.CONFIGURATION, BeanIsAbstractException.class);
        registerType(Type.CONFIGURATION, BeanIsNotAFactoryException.class);
        registerType(Type.CONFIGURATION, BeanNotOfRequiredTypeException.class);
        registerType(Type.CONFIGURATION, ConversionNotSupportedException.class);
        registerType(Type.CONFIGURATION, ConverterNotFoundException.class);
        registerType(Type.CONFIGURATION, PlaceholderResolutionException.class);
        registerType(Type.CONFIGURATION, NoSuchMessageException.class);
        registerType(Type.CONFIGURATION, org.springframework.boot.context.properties.bind.BindException.class);
        registerType(Type.CONFIGURATION, BindValidationException.class);
        registerType(Type.CONFIGURATION, UnboundConfigurationPropertiesException.class);
        registerType(Type.CONFIGURATION, InvalidConfigurationPropertyNameException.class);
        registerType(Type.CONFIGURATION, InvalidConfigurationPropertyValueException.class);
        registerType(Type.CONFIGURATION, MutuallyExclusiveConfigurationPropertiesException.class);
        registerType(Type.CONFIGURATION, ConfigDataResourceNotFoundException.class);
        registerType(Type.CONFIGURATION, InactiveConfigDataAccessException.class);
        registerType(Type.CONFIGURATION, NoSuchSslBundleException.class);

        registerType(Type.CONNECTIVITY, PortInUseException.class);

        registerType(Type.OVERLOAD, TaskRejectedException.class);

        registerType(Type.ABORT, BeanCreationNotAllowedException.class);

        registerType(Type.ILLEGAL_INPUT, TypeMismatchException.class);
        registerType(Type.ILLEGAL_INPUT, InvalidPropertyException.class);
        registerType(Type.ILLEGAL_INPUT, NotReadablePropertyException.class);
        registerType(Type.ILLEGAL_INPUT, NotWritablePropertyException.class);
        registerType(Type.ILLEGAL_INPUT, NullValueInNestedPathException.class);
        registerType(Type.ILLEGAL_INPUT, ConversionFailedException.class);
        registerType(Type.ILLEGAL_INPUT, DataBufferLimitException.class);
        registerType(Type.ILLEGAL_INPUT, InvalidMimeTypeException.class);
        registerType(Type.ILLEGAL_INPUT, org.springframework.validation.BindException.class);
        registerType(Type.ILLEGAL_INPUT, MethodValidationException.class);

        registerType(Type.ILLEGAL_OUTPUT, EncodingException.class);

        registerType(Type.CORRUPTED_DATA, DecodingException.class);
    }

    private void onApplicationStartup(ApplicationStartedEvent event) {
        try {
            initializeService.initialize(event.getApplicationContext());
        } catch (Exception e) {
            LOGGER.info("Failed to initialize services, root cause: {}, skipping", getRootCauseDescription(e));
        }
    }

    private void initializeData() {
        try {
            new InitializeData().initialize();
        } catch (Exception e) {
            LOGGER.info("Failed to initialize data module, root cause: {}, skipping", getRootCauseDescription(e));
        }
    }

    private void initializeJpa() {
        try {
            new InitializeJpa().initialize();
        } catch (Exception e) {
            LOGGER.info("Failed to initialize JPA module, root cause: {}, skipping", getRootCauseDescription(e));
        }
    }

    private void initializeWeb() {
        try {
            new InitializeWeb().initialize();
        } catch (Exception e) {
            LOGGER.info("Failed to initialize Web module, root cause: {}, skipping", getRootCauseDescription(e));
        }
    }

    private void initializeSecurity() {
        try {
            new InitializeSecurity().initialize();
        } catch (Exception e) {
            LOGGER.info("Failed to initialize Security, root cause: {}, skipping", getRootCauseDescription(e));
        }
    }

    private void registerObjectSizes() {
        ObjectSizeEstimator sizeEstimator = ObjectSizeEstimator.get();
        // core interfaces, which will have one instance (or a limited number) per application
        sizeEstimator.registerShallowSizeOfSubclass(Environment.class, 500);
        sizeEstimator.registerShallowSizeOfSubclass(ResourceLoader.class, 100);
        sizeEstimator.registerShallowSizeOfSubclass(Validator.class, 100);
        sizeEstimator.registerShallowSizeOfSubclass(ApplicationContext.class, 500);
        sizeEstimator.registerShallowSizeOfSubclass(ObjectFactory.class, 100);
        sizeEstimator.registerShallowSizeOfSubclass(BeanFactoryAware.class, 100);
        sizeEstimator.registerShallowSizeOfSubclass(BeanPostProcessor.class, 100);
        sizeEstimator.registerShallowSizeOfSubclass(BeanFactoryPostProcessor.class, 100);
        sizeEstimator.registerShallowSizeOfSubclass(MessageSource.class, 200);
        sizeEstimator.registerShallowSizeOfSubclass(GenericConverter.class, 64);
        sizeEstimator.registerShallowSizeOfSubclass(ConversionService.class, 500);

        // looks at special cases (proxies, etc)
        sizeEstimator.registerShallowSize(new SpringObjectSizeEstimator());
        // core annotations, mostly for things used to wire beans, which are temporary
        sizeEstimator.registerShallowSize(Configuration.class, 100);
    }
}
