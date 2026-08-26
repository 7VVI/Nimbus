package com.nimbus.netdisk.controller;

import com.nimbus.common.model.PageQuery;
import com.nimbus.common.response.Result;
import com.nimbus.log.annotation.OperLog;
import com.nimbus.log.enums.BusinessType;
import com.nimbus.netdisk.model.dto.FolderCreateDTO;
import com.nimbus.netdisk.model.dto.FolderMoveDTO;
import com.nimbus.netdisk.model.dto.FolderRenameDTO;
import com.nimbus.netdisk.model.vo.BreadcrumbVO;
import com.nimbus.netdisk.model.vo.FolderContentVO;
import com.nimbus.netdisk.model.vo.FolderTreeVO;
import com.nimbus.netdisk.service.FolderService;
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
 * 文件夹接口: 创建/重命名/移动/目录树/面包屑/内容列表/回收
 */
@RestController
@RequestMapping("/api/netdisk/folder")
@RequiredArgsConstructor
public class FolderController {

    private final FolderService folderService;

    /** 新建文件夹 */
    @OperLog(title = "文件夹管理", businessType = BusinessType.INSERT)
    @PostMapping
    public Result<Long> create(@Valid @RequestBody FolderCreateDTO dto) {
        return Result.ok(folderService.createFolder(LoginHelper.getUserId(), dto));
    }

    /** 重命名 */
    @OperLog(title = "文件夹管理", businessType = BusinessType.UPDATE)
    @PutMapping
    public Result<Void> rename(@Valid @RequestBody FolderRenameDTO dto) {
        folderService.renameFolder(LoginHelper.getUserId(), dto);
        return Result.ok();
    }

    /** 移动 */
    @OperLog(title = "文件夹管理", businessType = BusinessType.UPDATE)
    @PutMapping("/move")
    public Result<Void> move(@Valid @RequestBody FolderMoveDTO dto) {
        folderService.moveFolder(LoginHelper.getUserId(), dto);
        return Result.ok();
    }

    /** 目录树 */
    @GetMapping("/tree")
    public Result<List<FolderTreeVO>> tree() {
        return Result.ok(folderService.getTree(LoginHelper.getUserId()));
    }

    /** 面包屑 */
    @GetMapping("/{id}/breadcrumb")
    public Result<List<BreadcrumbVO>> breadcrumb(@PathVariable Long id) {
        return Result.ok(folderService.getBreadcrumb(LoginHelper.getUserId(), id));
    }

    /** 文件夹内容: 子文件夹 + 文件分页 */
    @GetMapping("/{id}/content")
    public Result<FolderContentVO> content(@PathVariable Long id, @Valid PageQuery query,
                                           @RequestParam(value = "sortKey", required = false) String sortKey,
                                           @RequestParam(value = "order", required = false) String order) {
        return Result.ok(folderService.getContent(LoginHelper.getUserId(), id, query, sortKey, order));
    }

    /** 删除到回收站(含全部子树) */
    @OperLog(title = "文件夹管理", businessType = BusinessType.DELETE)
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        folderService.deleteToRecycle(LoginHelper.getUserId(), id);
        return Result.ok();
    }
}