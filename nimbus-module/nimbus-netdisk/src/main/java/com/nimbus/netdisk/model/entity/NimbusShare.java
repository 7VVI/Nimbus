package com.nimbus.netdisk.model.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.nimbus.mybatis.model.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 分享表, shortCode 为 62 进制短码
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("nimbus_share")
public class NimbusShare extends BaseEntity {

    /** 分享人用户 id */
    private Long userId;

    /** 短链码 */
    private String shortCode;

    /** 分享类型: 1公开 2密码 */
    private Integer shareType;

    /** 提取码(密码类型) */
    private String password;

    /** 权限: 1可预览 2可下载 3可转存 */
    private Integer permission;

    /** 有效期类型: 1永久 2按天数 */
    private Integer expireType;

    /** 过期时间, 永久为 null */
    private LocalDateTime expireTime;

    /** 浏览次数 */
    private Integer viewCount;

    /** 转存次数 */
    private Integer saveCount;

    /** 状态: 1有效 0已取消 */
    private Integer status;
}