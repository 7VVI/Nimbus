package com.nimbus.security.model;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;
import java.util.Set;

/**
 * 登录用户信息, 登录时组装并存入 Sa-Token TokenSession
 * 权限/角色在登录时一次性加载, StpInterfaceImpl 直接读取, 避免每次鉴权查库
 */
@Data
public class LoginUser implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 用户 id */
    private Long userId;

    /** 登录账号 */
    private String username;

    /** 用户昵称 */
    private String nickname;

    /** 角色权限字符串集合 */
    private List<String> roleKeys;

    /** 菜单权限码集合, 超管为 *:*:* */
    private Set<String> permissions;

    /** 角色明细(含数据范围), 供数据权限使用 */
    private List<RoleDTO> roles;

    /** 登录时间(毫秒时间戳) */
    private Long loginTime;

    /** 登录 IP */
    private String ip;
}