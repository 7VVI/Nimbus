package com.nimbus.system.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 用户注册入参
 */
@Data
public class RegisterDTO {

    /** 登录账号 */
    @NotBlank(message = "账号不能为空")
    @Size(min = 3, max = 30, message = "账号长度需在3-30之间")
    private String username;

    /** 明文密码 */
    @NotBlank(message = "密码不能为空")
    @Size(min = 6, max = 64, message = "密码长度需在6-64之间")
    private String password;

    /** 用户昵称 */
    @Size(max = 30, message = "昵称长度不能超过30")
    private String nickname;

    /** 邮箱 */
    @Size(max = 64, message = "邮箱长度不能超过64")
    private String email;
}