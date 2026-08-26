package com.nimbus.system.model.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 登录结果
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class LoginVO {

    /** 用户 id */
    private Long userId;

    /** 会话 token */
    private String token;
}