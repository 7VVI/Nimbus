package com.nimbus.storage.config;

import com.nimbus.storage.core.LocalStorageService;
import com.nimbus.storage.core.OssStorageService;
import com.nimbus.storage.core.StorageService;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * 存储组件装配, 通过 nimbus.storage.type 切换实现
 */
@AutoConfiguration
@EnableConfigurationProperties(StorageProperties.class)
public class StorageAutoConfiguration {

    /** 本地磁盘存储(默认, 开箱即用) */
    @Bean
    @ConditionalOnProperty(prefix = "nimbus.storage", name = "type", havingValue = "local", matchIfMissing = true)
    public StorageService localStorageService(StorageProperties properties) {
        return new LocalStorageService(properties);
    }

    /** S3 兼容对象存储(MinIO/阿里云 OSS 等) */
    @Bean
    @ConditionalOnProperty(prefix = "nimbus.storage", name = "type", havingValue = "oss")
    public StorageService ossStorageService(StorageProperties properties) {
        return new OssStorageService(properties);
    }
}