package com.nimbus.netdisk.model.dto;

import com.nimbus.netdisk.constant.NetdiskConstants;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 上传初始化入参(秒传检测 + 分片任务创建)
 */
@Data
public class UploadInitDTO {

    /** 文件名(含扩展名) */
    @NotBlank(message = "文件名不能为空")
    @Size(max = 255, message = "文件名长度不能超过255")
    private String fileName;

    /** 文件大小(bytes) */
    @NotNull(message = "文件大小不能为空")
    @Positive(message = "文件大小必须大于0")
    private Long fileSize;

    /** 内容 SHA-256(小写十六进制) */
    @NotBlank(message = "文件哈希不能为空")
    @Size(min = 64, max = 64, message = "文件哈希必须为64位SHA-256")
    private String fileHash;

    /** 所属文件夹 id, 0 表示根目录 */
    private Long folderId = NetdiskConstants.ROOT_FOLDER_ID;

    /** 文件 id, 非空表示上传新版本 */
    private Long fileId;
}