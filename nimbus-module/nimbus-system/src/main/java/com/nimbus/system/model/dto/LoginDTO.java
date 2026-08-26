package com.nimbus.system.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 账号密码登录入参
 */
@Data
public class LoginDTO {

    /** 登录账号 */
    @NotBlank(message = "账号不能为空")
    @Size(max = 30, message = "账号长度不能超过30")
    private String username;

    /** 明文密码 */
    @NotBlank(message = "密码不能为空")
    @Size(max = 64, message = "密码长度不能超过64")
    private String password;
}