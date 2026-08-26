package com.nimbus.system.controller;

import com.nimbus.common.response.Result;
import com.nimbus.log.annotation.OperLog;
import com.nimbus.log.enums.BusinessType;
import com.nimbus.security.utils.LoginHelper;
import com.nimbus.system.model.dto.QuotaUpgradeDTO;
import com.nimbus.system.model.vo.QuotaVO;
import com.nimbus.system.service.QuotaService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
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

    /** 升级扩容: 调整总容量(只增不减) */
    @OperLog(title = "存储配额", businessType = BusinessType.UPDATE)
    @PutMapping("/upgrade")
    public Result<QuotaVO> upgrade(@Valid @RequestBody QuotaUpgradeDTO dto) {
        return Result.ok(quotaService.upgrade(LoginHelper.getUserId(), dto.getTotalSize()));
    }
}