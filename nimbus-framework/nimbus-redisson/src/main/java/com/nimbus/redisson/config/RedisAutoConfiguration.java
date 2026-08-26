package com.nimbus.redisson.config;

import com.nimbus.redisson.utils.RedisUtils;
import org.redisson.api.RedissonClient;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Bean;

/**
 * Redis 组件装配
 * <p>
 * RedissonClient 由 redisson-spring-boot-starter 依据 spring.data.redis 配置自动构建,
 * 本组件在其之上提供 {@link RedisUtils} 门面
 */
@AutoConfiguration(afterName = "org.redisson.spring.starter.RedissonAutoConfigurationV2")
@ConditionalOnBean(RedissonClient.class)
public class RedisAutoConfiguration {

    @Bean
    public RedisUtils redisUtils(RedissonClient redissonClient) {
        return new RedisUtils(redissonClient);
    }
}