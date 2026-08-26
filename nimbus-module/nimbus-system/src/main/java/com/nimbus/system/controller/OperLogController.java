package com.nimbus.system.controller;

import cn.dev33.satoken.annotation.SaCheckRole;
import com.nimbus.common.model.PageQuery;
import com.nimbus.common.model.PageResult;
import com.nimbus.common.response.Result;
import com.nimbus.log.annotation.OperLog;
import com.nimbus.log.enums.BusinessType;
import com.nimbus.system.model.entity.NimbusOperLog;
import com.nimbus.system.service.OperLogService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 操作审计日志接口, 仅管理员可用
 */
@RestController
@RequestMapping("/api/system/operlog")
@SaCheckRole("admin")
@RequiredArgsConstructor
public class OperLogController {

    private final OperLogService operLogService;

    /** 分页查询审计日志 */
    @GetMapping("/page")
    public Result<PageResult<NimbusOperLog>> page(@Valid PageQuery query) {
        return Result.ok(operLogService.page(query));
    }

    /** 清空审计日志 */
    @OperLog(title = "审计日志", businessType = BusinessType.CLEAN)
    @DeleteMapping("/clean")
    public Result<Long> clean() {
        return Result.ok(operLogService.clean());
    }
}