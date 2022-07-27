package org.kin.framework.spring.beans;

import org.springframework.beans.factory.annotation.AnnotatedBeanDefinition;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.core.annotation.AnnotationAttributes;
import org.springframework.core.type.MethodMetadata;

import javax.annotation.Nullable;
import java.util.Map;
import java.util.Objects;

/**
 * spring bean definition工具类
 * @author huangjianqin
 * @date 2022/7/18
 */
public final class BeanDefinitionUtils {
    private static final AnnotationAttributes EMPTY_ANNOTATION_ATTRIBUTES = new AnnotationAttributes();

    private BeanDefinitionUtils() {
    }

    /**
     *
     * @return
     */
    @Nullable
    private static MethodMetadata getBeanFactoryMethodMetadata(ConfigurableListableBeanFactory beanFactory,
                                                               String beanName){
        try {
            BeanDefinition beanDefinition = beanFactory.getBeanDefinition(beanName);
            if (!(beanDefinition instanceof AnnotatedBeanDefinition)) {
                return null;
            }

            return ((AnnotatedBeanDefinition) beanDefinition).getFactoryMethodMetadata();
        } catch (Exception e) {
            //ignore
            //有些bean没有bean definition, 比如spring.cloud.config-org.springframework.cloud.bootstrap.config.PropertySourceBootstrapProperties
        }

        return null;
    }

    /**
     * 获取定义在spring bean factory method上的指定注解属性
     */
    public static AnnotationAttributes getBeanFactoryMethodAnnoAttributes(ConfigurableListableBeanFactory beanFactory,
                                                                    String beanName,
                                                                    Class<?> annotationClass){
        return getBeanFactoryMethodAnnoAttributes(beanFactory, beanName, annotationClass.getName());
    }

    /**
     * 获取定义在spring bean factory method上的指定注解属性
     */
    public static AnnotationAttributes getBeanFactoryMethodAnnoAttributes(ConfigurableListableBeanFactory beanFactory,
                                                                    String beanName,
                                                                    String annotationName){
        MethodMetadata factoryMethodMetadata = getBeanFactoryMethodMetadata(beanFactory, beanName);
        if (Objects.isNull(factoryMethodMetadata)) {
            return EMPTY_ANNOTATION_ATTRIBUTES;
        }

        Map<String, Object> annoAttrsMap = factoryMethodMetadata.getAnnotationAttributes(annotationName);
        if (Objects.isNull(annoAttrsMap)) {
            return EMPTY_ANNOTATION_ATTRIBUTES;
        }

        return new AnnotationAttributes(annoAttrsMap);
    }

    /**
     * 判断spring bean factory method是否带指定注解
     */
    public static boolean isBeanFactoryMethodAnnotated(ConfigurableListableBeanFactory beanFactory,
                                                 String beanName,
                                                 Class<?> annotationClass){
        return isBeanFactoryMethodAnnotated(beanFactory, beanName, annotationClass.getName());
    }

    /**
     * 判断spring bean factory method是否带指定注解
     */
    public static boolean isBeanFactoryMethodAnnotated(ConfigurableListableBeanFactory beanFactory,
                                                 String beanName,
                                                 String annotationName){
        MethodMetadata factoryMethodMetadata = getBeanFactoryMethodMetadata(beanFactory, beanName);
        if (Objects.isNull(factoryMethodMetadata)) {
            return false;
        }

        return factoryMethodMetadata.isAnnotated(annotationName);
    }
}
