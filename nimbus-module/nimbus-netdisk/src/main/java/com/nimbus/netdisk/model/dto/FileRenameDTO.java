package com.nimbus.netdisk.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 重命名文件入参
 */
@Data
public class FileRenameDTO {

    /** 新文件名(含扩展名) */
    @NotBlank(message = "文件名不能为空")
    @Size(max = 255, message = "文件名长度不能超过255")
    private String fileName;
}