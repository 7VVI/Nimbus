package com.nimbus.netdisk.model.vo;

import com.nimbus.common.model.PageResult;
import com.nimbus.netdisk.model.entity.NimbusFile;
import com.nimbus.netdisk.model.entity.NimbusFolder;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 文件夹内容: 子文件夹(无分页) + 文件(分页)
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class FolderContentVO {

    /** 子文件夹列表 */
    private List<NimbusFolder> folders;

    /** 文件分页 */
    private PageResult<NimbusFile> files;
}