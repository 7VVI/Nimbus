package com.nimbus.system.service;

import com.nimbus.system.model.vo.QuotaVO;

/**
 * 存储配额业务接口
 */
public interface QuotaService {

    /** 注册用户默认总容量: 128GB */
    long DEFAULT_TOTAL_SIZE = 128L * 1024 * 1024 * 1024;

    /** 创建用户默认配额 */
    void initQuota(Long userId);

    /** 查询用户配额(含已用/剩余), 走缓存 */
    QuotaVO getQuota(Long userId);

    /** 上传前检查配额, 超出抛业务异常 */
    void checkQuota(Long userId, long addSize);

    /** 用量增减(delta 可为负), 结果不小于 0 */
    void changeUsage(Long userId, long delta);

    /**
     * 升级扩容: 将总容量调整为 newTotalSize
     * 仅允许不小于当前总容量(扩容只增不减), 返回更新后的配额
     */
    QuotaVO upgrade(Long userId, long newTotalSize);
}