package com.nimbus.netdisk.model.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 移动文件夹入参
 */
@Data
public class FolderMoveDTO {

    /** 被移动文件夹 id */
    @NotNull(message = "文件夹 id 不能为空")
    private Long id;

    /** 目标父文件夹 id, 0 表示根目录 */
    @NotNull(message = "目标文件夹不能为空")
    private Long targetParentId;
}