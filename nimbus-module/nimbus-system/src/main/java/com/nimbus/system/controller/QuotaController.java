package com.nimbus.system.controller;

import com.nimbus.common.response.Result;
import com.nimbus.security.utils.LoginHelper;
import com.nimbus.system.model.vo.QuotaVO;
import com.nimbus.system.service.QuotaService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 存储配额接口
 */
@RestController
@RequestMapping("/api/quota")
@RequiredArgsConstructor
public class QuotaController {

    private final QuotaService quotaService;

    /** 查询当前用户配额 */
    @GetMapping
    public Result<QuotaVO> getQuota() {
        return Result.ok(quotaService.getQuota(LoginHelper.getUserId()));
    }
}