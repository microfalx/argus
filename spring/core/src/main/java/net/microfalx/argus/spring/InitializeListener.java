package net.microfalx.argus.spring;

import lombok.extern.slf4j.Slf4j;
import net.microfalx.jvm.ObjectSizeEstimator;
import org.springframework.beans.factory.BeanFactoryAware;
import org.springframework.beans.factory.ObjectFactory;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.context.event.ApplicationStartedEvent;
import org.springframework.boot.context.event.ApplicationStartingEvent;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.convert.ConversionService;
import org.springframework.core.convert.converter.GenericConverter;
import org.springframework.core.env.Environment;
import org.springframework.core.io.ResourceLoader;
import org.springframework.validation.Validator;

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
        registerObjectSizes();
        initializeData();
        initializeSecurity();
        initializeJpa();
        initializeWeb();
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
