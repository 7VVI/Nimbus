package com.nimbus.opendoc.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * 接口文档装配, 通过 nimbus.doc.enabled=false 关闭
 */
@AutoConfiguration
@EnableConfigurationProperties(OpenDocProperties.class)
@ConditionalOnProperty(prefix = "nimbus.doc", name = "enabled", havingValue = "true", matchIfMissing = true)
public class OpenDocAutoConfiguration {

    @Bean
    public OpenAPI nimbusOpenApi(OpenDocProperties properties) {
        return new OpenAPI().info(new Info()
            .title(properties.getTitle())
            .description(properties.getDescription())
            .version(properties.getVersion()));
    }
}