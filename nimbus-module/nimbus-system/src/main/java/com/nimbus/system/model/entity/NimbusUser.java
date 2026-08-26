package com.nimbus.system.model.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.nimbus.mybatis.model.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 用户表
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("nimbus_user")
public class NimbusUser extends BaseEntity {

    /** 登录账号 */
    private String username;

    /** 用户昵称 */
    private String nickname;

    /** 密码(BCrypt 哈希) */
    private String password;

    /** 邮箱 */
    private String email;

    /** 手机号 */
    private String phone;

    /** 头像地址 */
    private String avatar;

    /** 角色权限字符串, 默认 netdisk */
    private String roleKey;

    /** 状态: 1正常 0停用 */
    private Integer status;

    /** 最后登录 IP */
    private String loginIp;

    /** 最后登录时间 */
    private LocalDateTime loginDate;

    /** 备注 */
    private String remark;
}