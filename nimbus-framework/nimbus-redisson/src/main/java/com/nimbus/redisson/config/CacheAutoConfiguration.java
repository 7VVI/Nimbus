package com.nimbus.redisson.config;

import com.nimbus.redisson.constant.CacheNames;
import org.redisson.api.RedissonClient;
import org.redisson.spring.cache.CacheConfig;
import org.redisson.spring.cache.RedissonSpringCacheManager;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * Spring Cache 组件装配: 基于 Redisson 的 CacheManager, 支撑 @Cacheable/@CacheEvict
 * 未在配置表中的缓存名按永不过期处理, 新增缓存请在 {@link CacheNames} 登记并约定 ttl
 */
@AutoConfiguration(after = RedisAutoConfiguration.class, afterName = "org.redisson.spring.starter.RedissonAutoConfigurationV2")
@ConditionalOnBean(RedissonClient.class)
@EnableCaching
public class CacheAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(CacheManager.class)
    public CacheManager cacheManager(RedissonClient redissonClient) {
        Map<String, CacheConfig> config = new HashMap<>();
        config.put(CacheNames.SYS_CONFIG, new CacheConfig(Duration.ofMinutes(60).toMillis(), 0));
        config.put(CacheNames.SYS_USER_NAME, new CacheConfig(Duration.ofMinutes(30).toMillis(), 0));
        config.put(CacheNames.NETDISK_FILE_HASH, new CacheConfig(Duration.ofMinutes(30).toMillis(), 0));
        config.put(CacheNames.NETDISK_QUOTA, new CacheConfig(Duration.ofSeconds(60).toMillis(), 0));
        return new RedissonSpringCacheManager(redissonClient, config);
    }
}