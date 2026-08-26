package com.nimbus.netdisk.model.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 移动文件入参
 */
@Data
public class FileMoveDTO {

    /** 目标文件夹 id, 0 表示根目录 */
    @NotNull(message = "目标文件夹不能为空")
    private Long folderId;
}