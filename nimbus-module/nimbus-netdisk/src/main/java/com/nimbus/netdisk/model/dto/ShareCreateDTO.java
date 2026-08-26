package com.nimbus.netdisk.model.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * 创建分享入参
 */
@Data
public class ShareCreateDTO {

    /** 目标类型: 1文件 2文件夹 */
    @NotNull(message = "目标类型不能为空")
    private Integer targetType;

    /** 目标 id 列表 */
    @NotNull(message = "分享目标不能为空")
    @Size(min = 1, message = "分享目标不能为空")
    private List<Long> targetIds;

    /** 分享类型: 1公开 2密码 */
    @NotNull(message = "分享类型不能为空")
    private Integer shareType;

    /** 提取码(分享类型为密码时必填, service 层条件校验) */
    @Size(max = 32, message = "提取码长度不能超过32")
    private String password;

    /** 权限位掩码: 1可预览 2可下载 4可转存(可组合), 默认全选 */
    private Integer permission = 7;

    /** 有效期类型: 1永久 2按天数, 默认 1 */
    private Integer expireType = 1;

    /** 有效天数(有效期类型为按天数时必填) */
    private Integer expireDays;
}