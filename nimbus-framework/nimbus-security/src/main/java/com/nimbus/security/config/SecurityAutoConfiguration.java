package com.nimbus.security.config;

import cn.dev33.satoken.interceptor.SaInterceptor;
import cn.dev33.satoken.stp.StpInterface;
import cn.dev33.satoken.stp.StpUtil;
import com.nimbus.security.core.StpInterfaceImpl;
import com.nimbus.security.handler.SecurityExceptionHandler;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 认证组件装配: 注册 Sa-Token 登录校验拦截器
 * <p>
 * 通过 nimbus.security.enabled=false 可整体关闭(拦截器不注册, 但 StpUtil 登录能力仍可用)
 */
@AutoConfiguration
@EnableConfigurationProperties(SecurityProperties.class)
public class SecurityAutoConfiguration {

    /** Sa-Token 异常统一翻译为业务响应体 */
    @Bean
    public SecurityExceptionHandler securityExceptionHandler() {
        return new SecurityExceptionHandler();
    }

    /** 权限数据源: 从会话 LoginUser 读取权限码/角色, 支撑注解鉴权 */
    @Bean
    @ConditionalOnMissingBean(StpInterface.class)
    public StpInterface stpInterface() {
        return new StpInterfaceImpl();
    }

    @Bean
    @ConditionalOnProperty(prefix = "nimbus.security", name = "enabled", havingValue = "true", matchIfMissing = true)
    public WebMvcConfigurer saTokenWebMvcConfigurer(SecurityProperties properties) {
        return new WebMvcConfigurer() {
            @Override
            public void addInterceptors(InterceptorRegistry registry) {
                registry.addInterceptor(new SaInterceptor(handle -> StpUtil.checkLogin()))
                    .addPathPatterns("/**")
                    .excludePathPatterns(properties.mergedIgnoreUrls());
            }
        };
    }
}