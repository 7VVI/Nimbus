package com.nimbus.system.model.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.nimbus.mybatis.model.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 用户存储配额表
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("nimbus_quota")
public class NimbusQuota extends BaseEntity {

    /** 所属用户 id */
    private Long userId;

    /** 总容量(bytes) */
    private Long totalSize;

    /** 已用容量(bytes) */
    private Long usedSize;
}