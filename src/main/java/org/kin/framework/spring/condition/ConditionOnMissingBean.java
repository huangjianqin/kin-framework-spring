package org.kin.framework.spring.condition;

import org.springframework.context.annotation.Conditional;

import java.lang.annotation.*;

/**
 * @author huangjianqin
 * @date 2020/12/27
 */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Conditional(MissingBeanCondition.class)
public @interface ConditionOnMissingBean {
    /**
     * 缺失的类
     */
    Class<?>[] value() default {};
}
