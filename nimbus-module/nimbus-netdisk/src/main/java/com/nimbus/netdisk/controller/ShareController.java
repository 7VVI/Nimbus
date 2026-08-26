package com.nimbus.netdisk.controller;

import com.nimbus.common.model.PageQuery;
import com.nimbus.common.model.PageResult;
import com.nimbus.common.response.Result;
import com.nimbus.log.annotation.OperLog;
import com.nimbus.log.enums.BusinessType;
import com.nimbus.netdisk.model.dto.ShareAccessDTO;
import com.nimbus.netdisk.model.dto.ShareCreateDTO;
import com.nimbus.netdisk.model.dto.ShareSaveDTO;
import com.nimbus.netdisk.model.entity.NimbusFile;
import com.nimbus.netdisk.model.entity.NimbusShare;
import com.nimbus.netdisk.model.vo.ShareAccessVO;
import com.nimbus.netdisk.model.vo.ShareItemVO;
import com.nimbus.netdisk.service.DownloadService;
import com.nimbus.netdisk.service.ShareService;
import com.nimbus.security.utils.LoginHelper;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * 分享接口: 创建/访问(免登录)/浏览/下载/转存/取消
 */
@RestController
@RequestMapping("/api/share")
@RequiredArgsConstructor
public class ShareController {

    private final ShareService shareService;

    private final DownloadService downloadService;

    /** 创建分享 */
    @OperLog(title = "文件分享", businessType = BusinessType.INSERT)
    @PostMapping
    public Result<NimbusShare> create(@Valid @RequestBody ShareCreateDTO dto) {
        return Result.ok(shareService.create(LoginHelper.getUserId(), dto));
    }

    /** 访问分享(免登录), 校验提取码与有效期 */
    @PostMapping("/access")
    public Result<ShareAccessVO> access(@Valid @RequestBody ShareAccessDTO dto) {
        return Result.ok(shareService.access(dto));
    }

    /** 分享内目录浏览(免登录), folderId 为空返回首层 */
    @GetMapping("/{code}/items")
    public Result<List<ShareItemVO>> items(@PathVariable String code,
                                           @RequestParam(required = false) Long folderId,
                                           @RequestParam(required = false) String password) {
        return Result.ok(shareService.listItems(code, password, folderId));
    }

    /** 分享内下载(免登录), 校验归属与下载权限 */
    @GetMapping("/{code}/download/{fileId}")
    public ResponseEntity<?> download(@PathVariable String code, @PathVariable Long fileId,
                                      @RequestParam(required = false) String password) {
        NimbusFile file = shareService.getShareDownloadFile(code, password, fileId);
        String url = downloadService.accessUrl(file, false);
        if (url != null) {
            return ResponseEntity.status(HttpStatus.FOUND).location(URI.create(url)).build();
        }
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
        headers.setContentDisposition(ContentDisposition.attachment()
            .filename(file.getFileName(), StandardCharsets.UTF_8).build());
        if (file.getFileSize() != null) {
            headers.setContentLength(file.getFileSize());
        }
        return new ResponseEntity<>(new InputStreamResource(downloadService.openFile(file)), headers, HttpStatus.OK);
    }

    /** 我的分享 */
    @GetMapping("/my")
    public Result<PageResult<NimbusShare>> my(@Valid PageQuery query) {
        return Result.ok(shareService.myShares(LoginHelper.getUserId(), query));
    }

    /** 取消分享 */
    @OperLog(title = "文件分享", businessType = BusinessType.DELETE)
    @DeleteMapping("/{id}")
    public Result<Void> cancel(@PathVariable Long id) {
        shareService.cancel(LoginHelper.getUserId(), id);
        return Result.ok();
    }

    /** 转存到我的网盘 */
    @OperLog(title = "文件分享", businessType = BusinessType.IMPORT)
    @PostMapping("/save")
    public Result<Long> save(@Valid @RequestBody ShareSaveDTO dto) {
        return Result.ok(shareService.save(LoginHelper.getUserId(), dto));
    }
}