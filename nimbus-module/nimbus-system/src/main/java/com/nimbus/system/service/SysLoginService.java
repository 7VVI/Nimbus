package com.nimbus.system.service;

import com.nimbus.system.model.dto.RegisterDTO;

/**
 * 认证业务接口
 */
public interface SysLoginService {

    /** 账号密码登录, 返回会话 token */
    String login(String username, String password, String ip, String userAgent);

    /** 退出登录 */
    void logout();

    /** 用户注册, 初始化默认配额, 返回用户 id */
    Long register(RegisterDTO dto);
}