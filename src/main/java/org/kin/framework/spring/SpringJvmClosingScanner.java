package org.kin.framework.spring;

import org.kin.framework.Closeable;
import org.kin.framework.JvmCloseCleaner;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;

import java.util.Map;

/**
 * 在spring容器中扫描Closeable实现类, 并添加进释放资源队列中
 *
 * @author huangjianqin
 * @date 2019/5/29
 */
public class SpringJvmClosingScanner implements ApplicationContextAware {
    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        Map<String, Closeable> beanMap = applicationContext.getBeansOfType(Closeable.class);
        JvmCloseCleaner.instance().addAll(beanMap.values());
    }
}
