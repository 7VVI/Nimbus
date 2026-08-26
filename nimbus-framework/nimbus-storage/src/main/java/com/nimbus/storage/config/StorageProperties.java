package com.nimbus.storage.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 存储组件配置项, nimbus.storage.type=local 使用本地磁盘, oss 使用 S3 兼容对象存储
 */
@Data
@ConfigurationProperties(prefix = "nimbus.storage")
public class StorageProperties {

    /** 存储类型: local 本地磁盘(默认) | oss S3 兼容对象存储(MinIO/阿里云等) */
    private String type = "local";

    /** 本地磁盘配置 */
    private Local local = new Local();

    /** 对象存储配置 */
    private Oss oss = new Oss();

    @Data
    public static class Local {

        /** 存储根目录 */
        private String basePath = "./data/nimbus-storage";
    }

    @Data
    public static class Oss {

        /** 服务地址(不含协议), 如 127.0.0.1:9000 */
        private String endpoint;

        /** 桶名称 */
        private String bucketName = "nimbus";

        /** AccessKey */
        private String accessKey;

        /** SecretKey */
        private String secretKey;

        /** 区域, 默认 us-east-1 对 MinIO 兼容 */
        private String region = "us-east-1";

        /** 是否 HTTPS */
        private boolean https = false;

        /** 公开访问域名(可选), 配置后优先用于拼装公开地址 */
        private String domain;
    }
}