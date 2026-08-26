package com.nimbus.netdisk.model.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 合并分片入参, 其余信息从上传任务缓存中读取
 */
@Data
public class UploadMergeDTO {

    /** 上传任务 id */
    @NotBlank(message = "上传任务 id 不能为空")
    private String uploadId;
}