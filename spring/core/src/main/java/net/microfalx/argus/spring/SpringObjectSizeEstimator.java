package net.microfalx.argus.spring;

import lombok.extern.slf4j.Slf4j;
import net.microfalx.jvm.ObjectSize;
import net.microfalx.jvm.ObjectSizeEstimator;
import net.microfalx.lang.ClassUtils;
import org.springframework.aop.support.AopUtils;

import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.function.Function;

@Slf4j
public class SpringObjectSizeEstimator implements Function<Object, ObjectSize> {

    private static final Set<Class<?>> springBootClasses = new CopyOnWriteArraySet<>();
    private final ObjectSizeEstimator sizeEstimator = ObjectSizeEstimator.get();

    @Override
    public ObjectSize apply(Object object) {
        Class<?> clazz = object.getClass();
        String className = clazz.getName();
        if (className.startsWith("org.springframework.") && springBootClasses.add(clazz)) {
            LOGGER.info("Unhandled Spring Boot class {}, current object {}, top object {}",
                    className, ClassUtils.getName(ObjectSizeEstimator.current()),
                    ClassUtils.getName(ObjectSizeEstimator.top()));
            // for now, just return a default size, we need to
            return ObjectSize.of(50);
        }
        if (AopUtils.isAopProxy(object)) {
            if (AopUtils.isCglibProxy(object)) {
                // CGLIB proxy
            } else if (AopUtils.isJdkDynamicProxy(object)) {
                // JDK dynamic proxy
            }
            Class<?> type = AopUtils.getTargetClass(object);
            return ObjectSize.of(sizeEstimator.getShallowSize(type));
        } else {
            return null;
        }
    }
}
