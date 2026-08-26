package com.nimbus.system.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.nimbus.common.exception.BusinessException;
import com.nimbus.common.exception.ErrorCode;
import com.nimbus.redisson.constant.CacheNames;
import com.nimbus.redisson.utils.RedisUtils;
import com.nimbus.system.mapper.NimbusQuotaMapper;
import com.nimbus.system.model.entity.NimbusQuota;
import com.nimbus.system.model.vo.QuotaVO;
import com.nimbus.system.service.QuotaService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;

/**
 * 存储配额业务实现: DB 为准, Redis 缓存热读, 用量变更即失效缓存
 */
@Service
@RequiredArgsConstructor
public class QuotaServiceImpl implements QuotaService {

    private static final Duration CACHE_TTL = Duration.ofSeconds(60);

    private final NimbusQuotaMapper nimbusQuotaMapper;

    private final RedisUtils redisUtils;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void initQuota(Long userId) {
        NimbusQuota quota = new NimbusQuota();
        quota.setUserId(userId);
        quota.setTotalSize(DEFAULT_TOTAL_SIZE);
        quota.setUsedSize(0L);
        nimbusQuotaMapper.insert(quota);
    }

    @Override
    public QuotaVO getQuota(Long userId) {
        NimbusQuota quota = getQuotaEntity(userId);
        return new QuotaVO(quota.getTotalSize(), quota.getUsedSize(),
            Math.max(0, quota.getTotalSize() - quota.getUsedSize()));
    }

    @Override
    public void checkQuota(Long userId, long addSize) {
        QuotaVO quota = getQuota(userId);
        if (quota.getUsedSize() + addSize > quota.getTotalSize()) {
            throw new BusinessException(ErrorCode.QUOTA_EXCEEDED, "存储空间不足, 已用 "
                + quota.getUsedSize() / 1024 / 1024 / 1024 + "GB, 请清理文件或升级扩容");
        }
    }

        @Override
    @Transactional(rollbackFor = Exception.class)
    public void changeUsage(Long userId, long delta) {
        NimbusQuota quota = getQuotaEntity(userId);
        Long usedSize = Math.max(0, quota.getUsedSize() + delta);
        NimbusQuota update = new NimbusQuota();
        update.setId(quota.getId());
        update.setUsedSize(usedSize);
        nimbusQuotaMapper.updateById(update);
        clearCache(userId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public QuotaVO upgrade(Long userId, long newTotalSize) {
        NimbusQuota quota = getQuotaEntity(userId);
        // 扩容只增不减, 且不能低于已用空间
        if (newTotalSize < quota.getTotalSize()) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "新容量不能小于当前总容量");
        }
        if (newTotalSize < quota.getUsedSize()) {
            throw new BusinessException(ErrorCode.QUOTA_EXCEEDED, "新容量不能低于已用空间");
        }
        NimbusQuota update = new NimbusQuota();
        update.setId(quota.getId());
        update.setTotalSize(newTotalSize);
        nimbusQuotaMapper.updateById(update);
        clearCache(userId);
        return getQuota(userId);
    }

    /** 读取配额, 缓存未命中回源 DB */
    private NimbusQuota getQuotaEntity(Long userId) {
        String key = cacheKey(userId);
        NimbusQuota quota = redisUtils.get(key);
        if (quota != null) {
            return quota;
        }
        quota = nimbusQuotaMapper.selectOne(new LambdaQueryWrapper<NimbusQuota>()
            .eq(NimbusQuota::getUserId, userId));
        if (quota == null) {
            throw new BusinessException(ErrorCode.DATA_NOT_EXIST, "用户配额不存在: " + userId);
        }
        redisUtils.set(key, quota, CACHE_TTL);
        return quota;
    }

    private void clearCache(Long userId) {
        redisUtils.delete(cacheKey(userId));
    }

    private String cacheKey(Long userId) {
        return CacheNames.NETDISK_QUOTA + ":" + userId;
    }
}