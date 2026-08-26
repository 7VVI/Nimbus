package com.nimbus.netdisk.model.dto;

import com.nimbus.netdisk.constant.NetdiskConstants;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 新建文件夹入参
 */
@Data
public class FolderCreateDTO {

    /** 父文件夹 id, 0 表示根目录 */
    private Long parentId = NetdiskConstants.ROOT_FOLDER_ID;

    /** 文件夹名称 */
    @NotBlank(message = "文件夹名称不能为空")
    @Size(max = 255, message = "文件夹名称长度不能超过255")
    private String folderName;
}