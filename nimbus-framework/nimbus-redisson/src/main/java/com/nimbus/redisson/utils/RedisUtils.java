package com.nimbus.redisson.utils;

import org.redisson.api.RAtomicLong;
import org.redisson.api.RBucket;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.redisson.api.redisnode.RedisNode;
import org.redisson.api.redisnode.RedisNodes;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * Redis 操作门面, 屏蔽 Redisson API 细节
 */
public class RedisUtils {

    private final RedissonClient client;

    public RedisUtils(RedissonClient client) {
        this.client = client;
    }

    public RedissonClient client() {
        return client;
    }

    /** 写入缓存 */
    public <T> void set(String key, T value) {
        client.<T>getBucket(key).set(value);
    }

    /** 写入缓存并设置过期时间 */
    public <T> void set(String key, T value, Duration ttl) {
        client.<T>getBucket(key).set(value, ttl);
    }

    /** 读取缓存 */
    public <T> T get(String key) {
        RBucket<T> bucket = client.getBucket(key);
        return bucket.get();
    }

    /** 删除缓存 */
    public boolean delete(String key) {
        return client.getBucket(key).delete();
    }

    /** 是否存在 */
    public boolean hasKey(String key) {
        return client.getBucket(key).isExists();
    }

    /** 设置过期时间 */
    public boolean expire(String key, Duration ttl) {
        return client.getBucket(key).expire(ttl);
    }

    /** 自增并返回结果 */
    public long increment(String key) {
        RAtomicLong atomic = client.getAtomicLong(key);
        return atomic.incrementAndGet();
    }

    /** 获取分布式锁(未加锁, 由调用方 lock/unlock) */
    public RLock getLock(String key) {
        return client.getLock(key);
    }

    /** 按模式扫描键(如 netdisk_upload_chunk:uploadId:*), 供断点续传/task 清理使用 */
    public Collection<String> keys(String pattern) {
        List<String> keys = new ArrayList<>();
        client.getKeys().getKeysByPattern(pattern).forEach(keys::add);
        return keys;
    }

    /** 剩余过期时间(毫秒), -1 永不过期, -2 键不存在 */
    public long ttl(String key) {
        return client.getBucket(key).remainTimeToLive();
    }

    /** 按模式删除键, 返回删除数量 */
    public long deleteByPattern(String pattern) {
        return client.getKeys().deleteByPattern(pattern);
    }

    /** 当前库键总数 */
    public long dbSize() {
        return client.getKeys().count();
    }

    /** Redis 服务器信息(INFO ALL), 供缓存监控使用 */
    public Map<String, String> getInfo() {
        return client.getRedisNodes(RedisNodes.SINGLE).getInstance().info(RedisNode.InfoSection.ALL);
    }

    /** Redis 命令统计(INFO COMMANDSTATS), 供缓存监控使用 */
    public Map<String, String> getCommandStats() {
        return client.getRedisNodes(RedisNodes.SINGLE).getInstance().info(RedisNode.InfoSection.COMMANDSTATS);
    }
}