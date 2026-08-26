package com.nimbus.netdisk.controller;

import com.nimbus.common.model.PageQuery;
import com.nimbus.common.model.PageResult;
import com.nimbus.common.response.Result;
import com.nimbus.log.annotation.OperLog;
import com.nimbus.log.enums.BusinessType;
import com.nimbus.netdisk.constant.NetdiskConstants;
import com.nimbus.netdisk.model.vo.RecycleItemVO;
import com.nimbus.netdisk.service.RecycleService;
import com.nimbus.security.utils.LoginHelper;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 回收站接口: 列表/恢复/彻底删除/清空
 */
@RestController
@RequestMapping("/api/recycle")
@RequiredArgsConstructor
public class RecycleController {

    private final RecycleService recycleService;

    /** 回收站列表 */
    @GetMapping("/page")
    public Result<PageResult<RecycleItemVO>> page(@Valid PageQuery query) {
        return Result.ok(recycleService.page(LoginHelper.getUserId(), query));
    }

    /** 恢复(原位置不可用时回退根目录) */
    @OperLog(title = "回收站", businessType = BusinessType.UPDATE)
    @PutMapping("/restore")
    public Result<Void> restore(@RequestParam Integer targetType, @RequestParam Long id) {
        recycleService.restore(LoginHelper.getUserId(), targetType, id);
        return Result.ok();
    }

    /** 彻底删除 */
    @OperLog(title = "回收站", businessType = BusinessType.DELETE)
    @DeleteMapping("/{targetType}/{id}")
    public Result<Void> purge(@PathVariable Integer targetType, @PathVariable Long id) {
        recycleService.purge(LoginHelper.getUserId(), targetType, id);
        return Result.ok();
    }

    /** 清空回收站 */
    @OperLog(title = "回收站", businessType = BusinessType.CLEAN)
    @DeleteMapping("/clean")
    public Result<Long> clean() {
        return Result.ok(recycleService.clean(LoginHelper.getUserId()));
    }
}