package net.microfalx.argus.spring;

import lombok.extern.slf4j.Slf4j;
import net.microfalx.lang.AnnotationUtils;
import org.springframework.context.ApplicationContext;

import java.util.Collection;
import java.util.concurrent.CopyOnWriteArrayList;

@Slf4j
class InitializeService {

    private final Collection<Object> services = new CopyOnWriteArrayList<>();

    void initialize(ApplicationContext applicationContext) {
        Collection<Object> beans = applicationContext.getBeansOfType(Object.class).values();
        for (Object bean : beans) {
            if (!isService(bean)) continue;
            // keep a reference, since the service locator keeps a weak reference to the service
            services.add(bean);
            // TODO not ready yet
            //ServiceLocator.register(Service.proxy(bean));
        }
        LOGGER.info("Registered {} beans as services", services.size());
    }

    private boolean isService(Object bean) {
        return AnnotationUtils.getAnnotation(bean, org.springframework.stereotype.Service.class) != null;
    }
}
