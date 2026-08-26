package com.nimbus.opendoc.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 接口文档配置项
 */
@Data
@ConfigurationProperties(prefix = "nimbus.doc")
public class OpenDocProperties {

    /** 是否启用, 默认启用 */
    private boolean enabled = true;

    /** 文档标题 */
    private String title = "nimbus-cloud API";

    /** 文档描述 */
    private String description = "nimbus-cloud 企业级网盘服务端接口文档";

    /** 文档版本 */
    private String version = "1.0.0";
}