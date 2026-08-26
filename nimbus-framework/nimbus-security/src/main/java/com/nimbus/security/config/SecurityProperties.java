package com.nimbus.security.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * 安全组件配置项
 */
@Data
@ConfigurationProperties(prefix = "nimbus.security")
public class SecurityProperties {

    /** 是否启用登录校验拦截, 默认启用 */
    private boolean enabled = true;

    /** 免登录路径, 在默认白名单基础上追加 */
    private List<String> ignoreUrls = new ArrayList<>();

    /** 默认白名单: 健康检查/接口文档/错误页 */
    public static final List<String> DEFAULT_IGNORE_URLS = List.of(
        "/api/health",
        "/actuator/**",
        "/v3/api-docs/**",
        "/swagger-ui/**",
        "/swagger-ui.html",
        "/error",
        "/favicon.ico"
    );

    /** 合并默认白名单与自定义白名单 */
    public List<String> mergedIgnoreUrls() {
        List<String> merged = new ArrayList<>(DEFAULT_IGNORE_URLS);
        merged.addAll(ignoreUrls);
        return merged;
    }
}