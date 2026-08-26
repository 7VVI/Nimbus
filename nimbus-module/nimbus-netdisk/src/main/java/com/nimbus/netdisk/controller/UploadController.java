package com.nimbus.netdisk.controller;

import com.nimbus.common.response.Result;
import com.nimbus.log.annotation.OperLog;
import com.nimbus.log.enums.BusinessType;
import com.nimbus.netdisk.model.dto.UploadInitDTO;
import com.nimbus.netdisk.model.dto.UploadMergeDTO;
import com.nimbus.netdisk.model.entity.NimbusFile;
import com.nimbus.netdisk.model.vo.UploadInitVO;
import com.nimbus.netdisk.service.UploadService;
import com.nimbus.security.utils.LoginHelper;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

/**
 * 上传接口: 初始化(秒传检测) -> 分片上传(断点续传) -> 合并; 小文件可整体上传
 */
@RestController
@RequestMapping("/api/upload")
@RequiredArgsConstructor
public class UploadController {

    private final UploadService uploadService;

    /** 初始化上传, 秒传命中直接返回文件记录 */
    @OperLog(title = "文件上传", businessType = BusinessType.INSERT, saveResponseData = false)
    @PostMapping("/init")
    public Result<UploadInitVO> init(@Valid @RequestBody UploadInitDTO dto) {
        return Result.ok(uploadService.init(LoginHelper.getUserId(), dto));
    }

    /** 上传分片 */
    @OperLog(title = "文件上传", businessType = BusinessType.INSERT, saveRequestData = false, saveResponseData = false)
    @PostMapping("/chunk")
    public Result<Void> chunk(@RequestParam String uploadId, @RequestParam int chunkIndex,
                              @RequestParam("file") MultipartFile file) throws IOException {
        uploadService.uploadChunk(uploadId, chunkIndex, file.getBytes());
        return Result.ok();
    }

    /** 合并分片, 创建文件记录或提交新版本 */
    @OperLog(title = "文件上传", businessType = BusinessType.INSERT, saveResponseData = false)
    @PostMapping("/merge")
    public Result<NimbusFile> merge(@Valid @RequestBody UploadMergeDTO dto) {
        return Result.ok(uploadService.merge(LoginHelper.getUserId(), dto));
    }

    /** 小文件整体上传 */
    @OperLog(title = "文件上传", businessType = BusinessType.INSERT, saveRequestData = false, saveResponseData = false)
    @PostMapping("/single")
    public Result<NimbusFile> single(@RequestParam("file") MultipartFile file,
                                     @RequestParam(defaultValue = "0") Long folderId) {
        return Result.ok(uploadService.singleUpload(LoginHelper.getUserId(), file, folderId));
    }

    /** 取消上传, 清理分片 */
    @OperLog(title = "文件上传", businessType = BusinessType.DELETE, saveRequestData = false, saveResponseData = false)
    @DeleteMapping("/{uploadId}")
    public Result<Void> cancel(@PathVariable String uploadId) {
        uploadService.cancel(uploadId);
        return Result.ok();
    }
}