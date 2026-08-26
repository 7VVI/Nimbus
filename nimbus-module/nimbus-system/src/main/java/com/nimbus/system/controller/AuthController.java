package com.nimbus.system.controller;

import com.nimbus.common.response.Result;
import com.nimbus.common.utils.ServletUtils;
import com.nimbus.log.annotation.OperLog;
import com.nimbus.log.enums.BusinessType;
import com.nimbus.security.model.LoginUser;
import com.nimbus.security.utils.LoginHelper;
import com.nimbus.system.model.dto.LoginDTO;
import com.nimbus.system.model.dto.RegisterDTO;
import com.nimbus.system.model.vo.LoginUserVO;
import com.nimbus.system.model.vo.LoginVO;
import com.nimbus.system.service.SysLoginService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 认证接口: 注册/账号密码登录, 登录成功组装 LoginUser 并返回会话 token
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final SysLoginService sysLoginService;

    /** 用户注册, 初始化默认配额 */
    @PostMapping("/register")
    public Result<Long> register(@Valid @RequestBody RegisterDTO dto) {
        return Result.ok(sysLoginService.register(dto));
    }

    /** 账号密码登录, 返回会话 token */
    @PostMapping("/login")
    public Result<LoginVO> login(@Valid @RequestBody LoginDTO dto, HttpServletRequest request) {
        String ip = ServletUtils.getClientIp(request);
        String userAgent = request.getHeader("User-Agent");
        String token = sysLoginService.login(dto.getUsername(), dto.getPassword(), ip, userAgent);
        return Result.ok(new LoginVO(LoginHelper.getUserId(), token));
    }

    /** 查询当前登录用户, 未登录返回401 */
    @GetMapping("/me")
    public Result<LoginUserVO> me() {
        LoginUser loginUser = LoginHelper.getLoginUserOrNull();
        if (loginUser == null) {
            return Result.ok(LoginUserVO.builder().userId(LoginHelper.getUserId()).build());
        }
        return Result.ok(LoginUserVO.builder()
            .userId(loginUser.getUserId())
            .username(loginUser.getUsername())
            .nickname(loginUser.getNickname())
            .roleKeys(loginUser.getRoleKeys())
            .permissions(loginUser.getPermissions() == null ? null
                : loginUser.getPermissions().stream().toList())
            .build());
    }

    /** 退出登录 */
    @OperLog(title = "认证", businessType = BusinessType.OTHER)
    @PostMapping("/logout")
    public Result<Void> logout() {
        sysLoginService.logout();
        return Result.ok();
    }
}