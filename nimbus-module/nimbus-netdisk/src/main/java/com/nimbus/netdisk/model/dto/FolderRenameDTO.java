package com.nimbus.netdisk.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 重命名文件夹入参
 */
@Data
public class FolderRenameDTO {

    /** 文件夹 id */
    @NotNull(message = "文件夹 id 不能为空")
    private Long id;

    /** 新名称 */
    @NotBlank(message = "文件夹名称不能为空")
    @Size(max = 255, message = "文件夹名称长度不能超过255")
    private String folderName;
}