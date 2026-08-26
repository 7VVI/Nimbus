package com.nimbus.netdisk.controller;

import com.nimbus.common.model.PageResult;
import com.nimbus.common.response.Result;
import com.nimbus.netdisk.model.dto.FileQueryDTO;
import com.nimbus.netdisk.model.entity.NimbusFile;
import com.nimbus.netdisk.service.FileService;
import com.nimbus.security.utils.LoginHelper;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 搜索接口: 文件名关键字/分类/收藏过滤, 与文件列表共用查询能力
 */
@RestController
@RequestMapping("/api/search")
@RequiredArgsConstructor
public class SearchController {

    private final FileService fileService;

    /** 文件搜索 */
    @GetMapping("/file")
    public Result<PageResult<NimbusFile>> search(@Valid FileQueryDTO query) {
        return Result.ok(fileService.page(LoginHelper.getUserId(), query));
    }
}