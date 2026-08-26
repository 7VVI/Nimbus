package com.nimbus.netdisk.model.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.nimbus.mybatis.model.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 分享目标关联表
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("nimbus_share_item")
public class NimbusShareItem extends BaseEntity {

    /** 分享 id */
    private Long shareId;

    /** 目标类型: 1文件 2文件夹 */
    private Integer targetType;

    /** 目标 id */
    private Long targetId;
}