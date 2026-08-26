package com.nimbus.netdisk.model.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * 批量打包下载入参, 文件夹包含其全部子文件
 */
@Data
public class BatchDownloadDTO {

    /** 文件 id 列表 */
    @Size(max = 200, message = "单次选择文件过多")
    private List<Long> fileIds;

    /** 文件夹 id 列表 */
    @Size(max = 50, message = "单次选择文件夹过多")
    private List<Long> folderIds;
}