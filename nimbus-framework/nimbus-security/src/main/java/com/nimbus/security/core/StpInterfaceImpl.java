package com.nimbus.security.core;

import cn.dev33.satoken.stp.StpInterface;
import com.nimbus.security.model.LoginUser;
import com.nimbus.security.utils.LoginHelper;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Sa-Token 权限数据源: 从会话中的 LoginUser 读取权限码与角色, 支撑 @SaCheckPermission/@SaCheckRole
 * 未组装 LoginUser 的会话(如分享访问)返回空集合
 */
public class StpInterfaceImpl implements StpInterface {

    @Override
    public List<String> getPermissionList(Object loginId, String loginType) {
        LoginUser loginUser = LoginHelper.getLoginUserOrNull();
        if (loginUser == null || loginUser.getPermissions() == null) {
            return Collections.emptyList();
        }
        return new ArrayList<>(loginUser.getPermissions());
    }

    @Override
    public List<String> getRoleList(Object loginId, String loginType) {
        LoginUser loginUser = LoginHelper.getLoginUserOrNull();
        if (loginUser == null || loginUser.getRoleKeys() == null) {
            return Collections.emptyList();
        }
        return loginUser.getRoleKeys();
    }
}