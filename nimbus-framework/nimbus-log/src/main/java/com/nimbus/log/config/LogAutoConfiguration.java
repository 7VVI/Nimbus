package com.nimbus.log.config;

import com.nimbus.log.aspect.OperLogAspect;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;

/**
 * 日志组件自动装配, nimbus.log.enabled=false 可整体关闭
 */
@AutoConfiguration
@ConditionalOnProperty(prefix = "nimbus.log", name = "enabled", havingValue = "true", matchIfMissing = true)
public class LogAutoConfiguration {

    /** 操作日志切面 */
    @Bean
    public OperLogAspect operLogAspect(ApplicationEventPublisher publisher) {
        return new OperLogAspect(publisher);
    }
}