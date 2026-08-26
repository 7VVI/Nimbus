package com.nimbus.netdisk.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 转存分享到我的网盘入参
 */
@Data
public class ShareSaveDTO {

    /** 短链码 */
    @NotBlank(message = "分享码不能为空")
    private String code;

    /** 提取码(密码分享必填) */
    @Size(max = 32, message = "提取码长度不能超过32")
    private String password;

    /** 转存目标文件夹 id, 0 表示根目录 */
    @NotNull(message = "目标文件夹不能为空")
    private Long folderId;
}