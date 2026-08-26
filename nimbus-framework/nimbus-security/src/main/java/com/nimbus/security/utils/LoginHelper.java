package com.nimbus.security.utils;

import cn.dev33.satoken.stp.StpUtil;
import com.nimbus.security.model.LoginUser;

/**
 * 登录态门面, 封装 Sa-Token 常用操作, 业务代码不直接依赖 StpUtil
 */
public final class LoginHelper {

    /** TokenSession 中 LoginUser 的存储键 */
    public static final String LOGIN_USER_KEY = "loginUser";

    /** 超级管理员用户 id */
    public static final Long SUPER_ADMIN_ID = 1L;

    private LoginHelper() {
    }

    /** 执行登录, 返回本次会话 token */
    public static String login(Long userId) {
        StpUtil.login(userId);
        return StpUtil.getTokenValue();
    }

    /** 执行登录并将用户信息存入 TokenSession, 返回本次会话 token */
    public static String login(LoginUser loginUser) {
        StpUtil.login(loginUser.getUserId());
        StpUtil.getTokenSession().set(LOGIN_USER_KEY, loginUser);
        return StpUtil.getTokenValue();
    }

    /** 获取当前登录用户信息, 未登录抛出 NotLoginException */
    public static LoginUser getLoginUser() {
        return (LoginUser) StpUtil.getTokenSession().get(LOGIN_USER_KEY);
    }

    /** 获取当前登录用户信息, 未登录或会话未存 LoginUser 返回 null */
    public static LoginUser getLoginUserOrNull() {
        if (!StpUtil.isLogin()) {
            return null;
        }
        try {
            return (LoginUser) StpUtil.getTokenSession().get(LOGIN_USER_KEY);
        } catch (Exception e) {
            return null;
        }
    }

    /** 指定用户是否超级管理员 */
    public static boolean isSuperAdmin(Long userId) {
        return SUPER_ADMIN_ID.equals(userId);
    }

    /** 当前登录用户是否超级管理员 */
    public static boolean isSuperAdmin() {
        return isSuperAdmin(getUserIdOrNull());
    }

    /** 当前是否已登录 */
    public static boolean isLogin() {
        return StpUtil.isLogin();
    }

    /** 获取当前登录用户 id, 未登录抛出 NotLoginException */
    public static Long getUserId() {
        return StpUtil.getLoginIdAsLong();
    }

    /** 获取当前登录用户 id, 未登录返回 null */
    public static Long getUserIdOrNull() {
        Object loginId = StpUtil.getLoginIdDefaultNull();
        return loginId == null ? null : Long.parseLong(loginId.toString());
    }

    /** 退出登录 */
    public static void logout() {
        StpUtil.logout();
    }
}