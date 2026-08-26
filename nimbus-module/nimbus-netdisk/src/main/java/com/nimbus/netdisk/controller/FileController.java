package com.nimbus.netdisk.controller;

import com.nimbus.common.model.PageResult;
import com.nimbus.common.response.Result;
import com.nimbus.log.annotation.OperLog;
import com.nimbus.log.enums.BusinessType;
import com.nimbus.netdisk.model.dto.FileCopyDTO;
import com.nimbus.netdisk.model.dto.FileMoveDTO;
import com.nimbus.netdisk.model.dto.FileQueryDTO;
import com.nimbus.netdisk.model.dto.FileRenameDTO;
import com.nimbus.netdisk.model.entity.NimbusFile;
import com.nimbus.netdisk.model.entity.NimbusFileVersion;
import com.nimbus.netdisk.service.FileService;
import com.nimbus.netdisk.service.VersionService;
import com.nimbus.security.utils.LoginHelper;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 文件接口: 列表/搜索/详情/重命名/移动/复制/收藏/回收/版本
 */
@RestController
@RequestMapping("/api/netdisk/file")
@RequiredArgsConstructor
public class FileController {

    private final FileService fileService;

    private final VersionService versionService;

    /** 文件列表/搜索 */
    @GetMapping("/page")
    public Result<PageResult<NimbusFile>> page(@Valid FileQueryDTO query) {
        return Result.ok(fileService.page(LoginHelper.getUserId(), query));
    }

    /** 最近文件 */
    @GetMapping("/recent")
    public Result<List<NimbusFile>> recent(@RequestParam(defaultValue = "20") int limit) {
        return Result.ok(fileService.recent(LoginHelper.getUserId(), limit));
    }

    /** 收藏文件 */
    @GetMapping("/starred")
    public Result<List<NimbusFile>> starred() {
        return Result.ok(fileService.starred(LoginHelper.getUserId()));
    }

    /** 文件详情 */
    @GetMapping("/{id}")
    public Result<NimbusFile> detail(@PathVariable Long id) {
        return Result.ok(fileService.detail(LoginHelper.getUserId(), id));
    }

    /** 重命名 */
    @OperLog(title = "文件管理", businessType = BusinessType.UPDATE)
    @PutMapping("/{id}")
    public Result<Void> rename(@PathVariable Long id, @Valid @RequestBody FileRenameDTO dto) {
        fileService.rename(LoginHelper.getUserId(), id, dto);
        return Result.ok();
    }

    /** 移动 */
    @OperLog(title = "文件管理", businessType = BusinessType.UPDATE)
    @PutMapping("/{id}/move")
    public Result<Void> move(@PathVariable Long id, @Valid @RequestBody FileMoveDTO dto) {
        fileService.move(LoginHelper.getUserId(), id, dto);
        return Result.ok();
    }

    /** 复制(同目录自动追加序号) */
    @OperLog(title = "文件管理", businessType = BusinessType.INSERT)
    @PostMapping("/{id}/copy")
    public Result<NimbusFile> copy(@PathVariable Long id, @Valid @RequestBody FileCopyDTO dto) {
        return Result.ok(fileService.copy(LoginHelper.getUserId(), id, dto));
    }

    /** 设置收藏 */
    @OperLog(title = "文件管理", businessType = BusinessType.UPDATE)
    @PutMapping("/{id}/star")
    public Result<Void> star(@PathVariable Long id, @RequestParam boolean starred) {
        fileService.star(LoginHelper.getUserId(), id, starred);
        return Result.ok();
    }

    /** 删除到回收站 */
    @OperLog(title = "文件管理", businessType = BusinessType.DELETE)
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        fileService.deleteToRecycle(LoginHelper.getUserId(), id);
        return Result.ok();
    }

    /** 版本列表 */
    @GetMapping("/{id}/versions")
    public Result<List<NimbusFileVersion>> versions(@PathVariable Long id) {
        return Result.ok(versionService.list(LoginHelper.getUserId(), id));
    }

    /** 回滚到指定版本 */
    @OperLog(title = "文件管理", businessType = BusinessType.UPDATE)
    @PutMapping("/{id}/rollback/{versionId}")
    public Result<NimbusFile> rollback(@PathVariable Long id, @PathVariable Long versionId) {
        return Result.ok(versionService.rollback(LoginHelper.getUserId(), id, versionId));
    }
}