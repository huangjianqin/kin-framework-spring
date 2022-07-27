package org.kin.framework.spring.condition;

import org.kin.framework.utils.CollectionUtils;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.annotation.MergedAnnotation;
import org.springframework.core.annotation.MergedAnnotations;
import org.springframework.core.type.AnnotatedTypeMetadata;

import javax.annotation.Nonnull;
import java.util.Map;
import java.util.Objects;

/**
 * 简易版本
 * 自己实现的bean缺失条件过滤, 主要是因为不想依赖于spring-boot
 *
 * @author huangjianqin
 * @date 2020/12/27
 */
public class MissingBeanCondition implements Condition {
    @Override
    public boolean matches(@Nonnull ConditionContext conditionContext,
                           @Nonnull AnnotatedTypeMetadata annotatedTypeMetadata) {
        ConfigurableListableBeanFactory beanFactory = conditionContext.getBeanFactory();
        if (Objects.isNull(beanFactory)) {
            return false;
        }
        MergedAnnotations annotations = annotatedTypeMetadata.getAnnotations();
        if (annotations.isPresent(ConditionOnMissingBean.class)) {
            MergedAnnotation<ConditionOnMissingBean> anno = annotations.get(ConditionOnMissingBean.class);
            Class<?>[] missingClasses = anno.getClassArray("value");
            for (Class<?> missingClass : missingClasses) {
                Map<String, ?> beans = beanFactory.getBeansOfType(missingClass);
                if (CollectionUtils.isNonEmpty(beans)) {
                    return false;
                }
            }
        }

        return true;
    }
}
