package com.nimbus.security.model;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 登录用户的角色信息, 随 LoginUser 存入会话
 */
@Data
public class RoleDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 角色 id */
    private Long roleId;

    /** 角色权限字符串, 如 admin/netdisk */
    private String roleKey;

    /** 数据范围: 1全部 2自定义 3本部门 4本部门及以下 5仅本人 */
    private String dataScope;
}