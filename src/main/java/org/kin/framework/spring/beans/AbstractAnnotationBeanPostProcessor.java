package org.kin.framework.spring.beans;

import org.kin.framework.utils.CollectionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.PropertyValues;
import org.springframework.beans.factory.BeanCreationException;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.annotation.InjectionMetadata;
import org.springframework.beans.factory.config.SmartInstantiationAwareBeanPostProcessor;
import org.springframework.beans.factory.support.MergedBeanDefinitionPostProcessor;
import org.springframework.beans.factory.support.RootBeanDefinition;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.core.annotation.AnnotationAttributes;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.util.Assert;
import org.springframework.util.ClassUtils;
import org.springframework.util.ReflectionUtils;
import org.springframework.util.StringUtils;

import javax.annotation.Nonnull;
import java.beans.PropertyDescriptor;
import java.lang.annotation.Annotation;
import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import static org.springframework.aop.support.AopUtils.getTargetClass;
import static org.springframework.core.BridgeMethodResolver.findBridgedMethod;
import static org.springframework.core.BridgeMethodResolver.isVisibilityBridgeMethodPair;
import static org.springframework.core.GenericTypeResolver.resolveTypeArgument;

/**
 * 利用spring {@link SmartInstantiationAwareBeanPostProcessor} 处理自定义注解注入逻辑
 *
 * @author huangjianqin
 * @date 2020/12/14
 */
public abstract class AbstractAnnotationBeanPostProcessor implements SmartInstantiationAwareBeanPostProcessor, MergedBeanDefinitionPostProcessor, DisposableBean {
    private final Logger log = LoggerFactory.getLogger(getClass());
    /** 需处理的注解 */
    private final Class<? extends Annotation>[] annotationTypes;
    /** 注解元数据 */
    private final ConcurrentMap<String, AnnotatedInjectionMetadata> injectionMetadataCache =
            new ConcurrentHashMap<>(32);
    /** 被注入的对象缓存 */
    private final ConcurrentMap<String, Object> injectedObjectsCache = new ConcurrentHashMap<>(32);

    /**
     * @param annotationTypes the multiple types of {@link Annotation annotations}
     */
    protected AbstractAnnotationBeanPostProcessor(Class<? extends Annotation>... annotationTypes) {
        Assert.notEmpty(annotationTypes, "The argument of annotations' types must not empty");
        this.annotationTypes = annotationTypes;
    }

    /**
     * @return 需处理的注解
     */
    protected final Class<? extends Annotation>[] getAnnotationTypes() {
        return annotationTypes;
    }

    @Override
    public PropertyValues postProcessProperties(
            @Nonnull PropertyValues pvs, @Nonnull Object bean, @Nonnull String beanName) throws BeanCreationException {
        InjectionMetadata metadata = findInjectionMetadata(beanName, bean.getClass(), pvs);
        if (Objects.nonNull(metadata)) {
            try {
                metadata.inject(bean, beanName, pvs);
            } catch (BeanCreationException ex) {
                throw ex;
            } catch (Throwable ex) {
                throw new BeanCreationException(beanName, "Injection of @" + getAnnotationTypes()[0].getSimpleName()
                        + " dependencies is failed", ex);
            }
        }
        return pvs;
    }

    /**
     * @return 注解每个字段的数据
     */
    private AnnotationAttributes getAnnotationAttributes(AnnotatedElement annotatedElement, Class<? extends Annotation> annotationType) {
        //spring merged Annotation
        Annotation mergedAnnotation = AnnotatedElementUtils.getMergedAnnotation(annotatedElement, annotationType);
        if (Objects.nonNull(mergedAnnotation)) {
            return AnnotationUtils.getAnnotationAttributes(annotatedElement, mergedAnnotation);
        }

        Annotation annotation = annotatedElement.getAnnotation(annotationType);
        if (Objects.nonNull(annotation)) {
            return AnnotationUtils.getAnnotationAttributes(annotatedElement, annotation);
        }
        return null;
    }

    /**
     * Finds {@link InjectionMetadata.InjectedElement} Metadata from annotated fields
     *
     * @param beanClass The {@link Class} of Bean
     * @return non-null {@link List}
     */
    private List<AnnotatedFieldElement> findAnnotationedFieldMetadata(final Class<?> beanClass) {
        List<AnnotatedFieldElement> elements = new LinkedList<>();
        ReflectionUtils.doWithFields(beanClass, field -> {
            for (Class<? extends Annotation> annotationType : getAnnotationTypes()) {
                AnnotationAttributes attributes = getAnnotationAttributes(field, annotationType);
                if (Objects.isNull(attributes)) {
                    return;
                }
                if (Modifier.isStatic(field.getModifiers())) {
                    if (log.isWarnEnabled()) {
                        log.warn("@" + annotationType.getName() + " is not supported on static fields: " + field);
                    }
                    return;
                }
                elements.add(new AnnotatedFieldElement(field, attributes));
            }
        });

        return elements;
    }

    /**
     * Finds {@link InjectionMetadata.InjectedElement} Metadata from annotated methods
     *
     * @param beanClass The {@link Class} of Bean
     * @return non-null {@link List}
     */
    private List<AnnotatedMethodElement> findAnnotatedMethodMetadata(final Class<?> beanClass) {
        List<AnnotatedMethodElement> elements = new LinkedList<>();
        ReflectionUtils.doWithMethods(beanClass, method -> {
            Method bridgedMethod = findBridgedMethod(method);
            if (!isVisibilityBridgeMethodPair(method, bridgedMethod)) {
                return;
            }

            for (Class<? extends Annotation> annotationType : getAnnotationTypes()) {
                AnnotationAttributes attributes = getAnnotationAttributes(bridgedMethod, annotationType);
                if (Objects.isNull(attributes)) {
                    return;
                }

                if (method.equals(ClassUtils.getMostSpecificMethod(method, beanClass))) {
                    if (Modifier.isStatic(method.getModifiers())) {
                        if (log.isWarnEnabled()) {
                            log.warn("@" + annotationType.getName() + " annotation is not supported on static methods: " + method);
                        }
                        return;
                    }
                    if (method.getParameterTypes().length == 0) {
                        if (log.isWarnEnabled()) {
                            log.warn("@" + annotationType.getName() + " annotation should only be used on methods with parameters: " +
                                    method);
                        }
                    }
                    PropertyDescriptor pd = BeanUtils.findPropertyForMethod(bridgedMethod, beanClass);
                    elements.add(new AnnotatedMethodElement(method, pd, attributes));
                }
            }
        });

        return elements;
    }

    /**
     * 构建被注解元数据, 包含被注解字段和方法的元数据
     */
    private AnnotatedInjectionMetadata buildAnnotatedMetadata(final Class<?> beanClass) {
        List<AnnotatedFieldElement> annotationedFieldMetadata = findAnnotationedFieldMetadata(beanClass);
        List<AnnotatedMethodElement> annotatedMethodMetadata = findAnnotatedMethodMetadata(beanClass);
        if (CollectionUtils.isNonEmpty(annotationedFieldMetadata) || CollectionUtils.isNonEmpty(annotatedMethodMetadata)) {
            return new AnnotatedInjectionMetadata(beanClass, annotationedFieldMetadata, annotatedMethodMetadata);
        }
        return null;
    }

    /**
     * 构建被注解Field或者Method的注入元数据
     */
    private InjectionMetadata findInjectionMetadata(String beanName, Class<?> clazz, PropertyValues pvs) {
        // Fall back to class name as cache key, for backwards compatibility with custom callers.
        String cacheKey = (StringUtils.hasLength(beanName) ? beanName : clazz.getName());
        // Quick check on the concurrent map first, with minimal locking.
        AnnotatedInjectionMetadata metadata = this.injectionMetadataCache.get(cacheKey);
        if (InjectionMetadata.needsRefresh(metadata, clazz)) {
            synchronized (this.injectionMetadataCache) {
                metadata = this.injectionMetadataCache.get(cacheKey);
                if (InjectionMetadata.needsRefresh(metadata, clazz)) {
                    if (metadata != null) {
                        metadata.clear(pvs);
                    }
                    try {
                        metadata = buildAnnotatedMetadata(clazz);
                        if (Objects.nonNull(metadata)) {
                            this.injectionMetadataCache.put(cacheKey, metadata);
                        }
                    } catch (NoClassDefFoundError err) {
                        throw new IllegalStateException("Failed to introspect object class [" + clazz.getName() +
                                "] for annotation metadata: could not find class that it depends on", err);
                    }
                }
            }
        }
        return metadata;
    }


    @Override
    public void postProcessMergedBeanDefinition(@Nonnull RootBeanDefinition beanDefinition, @Nonnull Class<?> beanType, @Nonnull String beanName) {
        InjectionMetadata metadata = findInjectionMetadata(beanName, beanType, null);
        if (Objects.nonNull(metadata)) {
            metadata.checkConfigMembers(beanDefinition);
        }
    }

    @Override
    public void destroy() throws Exception {
        for (Object object : injectedObjectsCache.values()) {
            if (log.isDebugEnabled()) {
                log.debug(object + " was destroying!");
            }

            if (object instanceof DisposableBean) {
                ((DisposableBean) object).destroy();
            }
        }

        injectionMetadataCache.clear();
        injectedObjectsCache.clear();

        if (log.isDebugEnabled()) {
            log.debug(getClass() + " was destroying!");
        }
    }

    /**
     * Gets all injected-objects.
     *
     * @return non-null {@link Collection}
     */
    protected Collection<Object> getInjectedObjects() {
        return this.injectedObjectsCache.values();
    }

    /**
     * Get injected-object from specified {@link AnnotationAttributes annotation attributes} and Bean Class
     *
     * @param attributes      {@link AnnotationAttributes the annotation attributes}
     * @param bean            Current bean that will be injected
     * @param beanName        Current bean name that will be injected
     * @param injectedType    the type of injected-object
     * @param injectedElement {@link InjectionMetadata.InjectedElement}
     * @return An injected object
     * @throws Exception If getting is failed
     */
    protected Object getInjectedObject(AnnotationAttributes attributes, Object bean, String beanName, Class<?> injectedType,
                                       InjectionMetadata.InjectedElement injectedElement) throws Exception {

        String cacheKey = buildInjectedObjectCacheKey(attributes, bean, beanName, injectedType, injectedElement);

        Object injectedObject = injectedObjectsCache.get(cacheKey);

        if (injectedObject == null) {
            injectedObject = doGetInjectedBean(attributes, bean, beanName, injectedType, injectedElement);
            // Customized inject-object if necessary
            injectedObjectsCache.putIfAbsent(cacheKey, injectedObject);
        }

        return injectedObject;

    }

    /**
     * Subclass must implement this method to get injected-object.
     *
     * @param attributes      {@link AnnotationAttributes the annotation attributes}
     * @param bean            Current bean that will be injected
     * @param beanName        Current bean name that will be injected
     * @param injectedType    the type of injected-object
     * @param injectedElement {@link InjectionMetadata.InjectedElement}
     * @return The injected object
     * @throws Exception If resolving an injected object is failed.
     */
    protected abstract Object doGetInjectedBean(AnnotationAttributes attributes, Object bean, String beanName, Class<?> injectedType,
                                                InjectionMetadata.InjectedElement injectedElement) throws Exception;

    /**
     * Build a cache key for injected-object.
     *
     * @param attributes      {@link AnnotationAttributes the annotation attributes}
     * @param bean            Current bean that will be injected
     * @param beanName        Current bean name that will be injected
     * @param injectedType    the type of injected-object
     * @param injectedElement {@link InjectionMetadata.InjectedElement}
     * @return Bean cache key
     */
    protected abstract String buildInjectedObjectCacheKey(AnnotationAttributes attributes, Object bean, String beanName,
                                                          Class<?> injectedType,
                                                          InjectionMetadata.InjectedElement injectedElement);

    //----------------------------------------------------------------------------------------------------------------
    @SafeVarargs
    private static Collection<InjectionMetadata.InjectedElement> combine(Collection<? extends InjectionMetadata.InjectedElement>... elements) {
        List<InjectionMetadata.InjectedElement> allElements = new ArrayList<>();
        for (Collection<? extends InjectionMetadata.InjectedElement> e : elements) {
            allElements.addAll(e);
        }
        return allElements;
    }

    /**
     * {@link Annotation Annotated} {@link InjectionMetadata} implementation
     */
    private class AnnotatedInjectionMetadata extends InjectionMetadata {
        /** 被注解字段元数据 */
        private final Collection<AnnotatedFieldElement> fieldElements;
        /** 被注解方法元数据 */
        private final Collection<AnnotatedMethodElement> methodElements;

        public AnnotatedInjectionMetadata(Class<?> targetClass, Collection<AnnotatedFieldElement> fieldElements,
                                          Collection<AnnotatedMethodElement> methodElements) {
            super(targetClass, combine(fieldElements, methodElements));
            this.fieldElements = fieldElements;
            this.methodElements = methodElements;
        }

        public Collection<AnnotatedFieldElement> getFieldElements() {
            return fieldElements;
        }

        public Collection<AnnotatedMethodElement> getMethodElements() {
            return methodElements;
        }
    }

    /**
     * {@link Annotation Annotated} {@link Method} {@link InjectionMetadata.InjectedElement}
     */
    private class AnnotatedMethodElement extends InjectionMetadata.InjectedElement {
        /** 被注解方法 */
        private final Method method;
        /** 被注解信息 */
        private final AnnotationAttributes attributes;

        protected AnnotatedMethodElement(Method method, PropertyDescriptor pd, AnnotationAttributes attributes) {
            super(method, pd);
            this.method = method;
            this.attributes = attributes;
        }

        @Override
        protected void inject(@Nonnull Object bean, String beanName, PropertyValues pvs) throws Throwable {
            if (Objects.isNull(pd)) {
                return;
            }
            Class<?> injectedType = pd.getPropertyType();
            Object injectedObject = getInjectedObject(attributes, bean, beanName, injectedType, this);
            ReflectionUtils.makeAccessible(method);
            method.invoke(bean, injectedObject);
        }
    }

    /**
     * {@link Annotation Annotated} {@link Field} {@link InjectionMetadata.InjectedElement}
     */
    public class AnnotatedFieldElement extends InjectionMetadata.InjectedElement {
        /** 被注解字段 */
        private final Field field;
        /** 被注解信息 */
        private final AnnotationAttributes attributes;

        protected AnnotatedFieldElement(Field field, AnnotationAttributes attributes) {
            super(field, null);
            this.field = field;
            this.attributes = attributes;
        }

        @Override
        protected void inject(@Nonnull Object bean, String beanName, PropertyValues pvs) throws Throwable {
            Class<?> injectedType = resolveInjectedType(bean, field);
            Object injectedObject = getInjectedObject(attributes, bean, beanName, injectedType, this);
            ReflectionUtils.makeAccessible(field);
            field.set(bean, injectedObject);
        }

        private Class<?> resolveInjectedType(Object bean, Field field) {
            Type genericType = field.getGenericType();
            if (genericType instanceof Class) {
                // Just a normal Class
                return field.getType();
            } else {
                // GenericType
                return resolveTypeArgument(getTargetClass(bean), field.getDeclaringClass());
            }
        }
    }
}