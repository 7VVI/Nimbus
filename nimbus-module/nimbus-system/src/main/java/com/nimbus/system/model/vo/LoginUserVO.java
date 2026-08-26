package com.nimbus.system.model.vo;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * 当前登录用户信息
 */
@Data
@Builder
public class LoginUserVO {

    /** 用户 id */
    private Long userId;

    /** 登录账号 */
    private String username;

    /** 用户昵称 */
    private String nickname;

    /** 角色权限字符串集合 */
    private List<String> roleKeys;

    /** 权限码集合 */
    private List<String> permissions;
}