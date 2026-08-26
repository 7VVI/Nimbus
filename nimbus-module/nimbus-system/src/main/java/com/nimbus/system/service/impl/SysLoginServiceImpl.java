package com.nimbus.system.service.impl;

import cn.dev33.satoken.secure.BCrypt;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.nimbus.common.exception.BusinessException;
import com.nimbus.common.exception.ErrorCode;
import com.nimbus.security.model.LoginUser;
import com.nimbus.security.utils.LoginHelper;
import com.nimbus.system.mapper.NimbusUserMapper;
import com.nimbus.system.model.dto.RegisterDTO;
import com.nimbus.system.model.entity.NimbusUser;
import com.nimbus.system.service.QuotaService;
import com.nimbus.system.service.SysLoginService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 认证业务实现: 账密校验 -> 组装 LoginUser -> 发布登录态; 注册默认配额
 */
@Service
@RequiredArgsConstructor
public class SysLoginServiceImpl implements SysLoginService {

    /** 超管权限通配符 */
    private static final String ALL_PERMISSION = "*:*:*";

    /** 默认角色 */
    private static final String DEFAULT_ROLE = "netdisk";

    private final NimbusUserMapper nimbusUserMapper;

    private final QuotaService quotaService;

    @Override
    public String login(String username, String password, String ip, String userAgent) {
        NimbusUser user = nimbusUserMapper.selectOne(new LambdaQueryWrapper<NimbusUser>()
            .eq(NimbusUser::getUsername, username));
        if (user == null || !BCrypt.checkpw(password, user.getPassword())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "账号或密码错误");
        }
        if (user.getStatus() == null || user.getStatus() != 1) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "账号已停用, 请联系管理员");
        }
        String token = LoginHelper.login(buildLoginUser(user, ip));
        NimbusUser update = new NimbusUser();
        update.setId(user.getId());
        update.setLoginIp(ip);
        update.setLoginDate(LocalDateTime.now());
        nimbusUserMapper.updateById(update);
        return token;
    }

    @Override
    public void logout() {
        LoginHelper.logout();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long register(RegisterDTO dto) {
        Long count = nimbusUserMapper.selectCount(new LambdaQueryWrapper<NimbusUser>()
            .eq(NimbusUser::getUsername, dto.getUsername()));
        if (count > 0) {
            throw new BusinessException(ErrorCode.DATA_ALREADY_EXIST, "账号已存在: " + dto.getUsername());
        }
        NimbusUser user = new NimbusUser();
        user.setUsername(dto.getUsername());
        user.setPassword(BCrypt.hashpw(dto.getPassword()));
        user.setNickname(dto.getNickname() == null || dto.getNickname().isBlank()
            ? dto.getUsername() : dto.getNickname());
        user.setEmail(dto.getEmail());
        user.setRoleKey(DEFAULT_ROLE);
        user.setStatus(1);
        nimbusUserMapper.insert(user);
        quotaService.initQuota(user.getId());
        return user.getId();
    }

    /** 组装登录用户信息, 角色与权限一次性加载进会话 */
    private LoginUser buildLoginUser(NimbusUser user, String ip) {
        LoginUser loginUser = new LoginUser();
        loginUser.setUserId(user.getId());
        loginUser.setUsername(user.getUsername());
        loginUser.setNickname(user.getNickname());
        loginUser.setRoleKeys(List.of(user.getRoleKey() == null || user.getRoleKey().isBlank()
            ? DEFAULT_ROLE : user.getRoleKey()));
        loginUser.setPermissions(LoginHelper.isSuperAdmin(user.getId())
            ? java.util.Set.of(ALL_PERMISSION) : java.util.Set.of());
        loginUser.setLoginTime(System.currentTimeMillis());
        loginUser.setIp(ip);
        return loginUser;
    }
}