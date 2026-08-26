package com.nimbus.netdisk.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 访问分享入参
 */
@Data
public class ShareAccessDTO {

    /** 短链码 */
    @NotBlank(message = "分享码不能为空")
    private String code;

    /** 提取码(密码分享必填) */
    @Size(max = 32, message = "提取码长度不能超过32")
    private String password;
}