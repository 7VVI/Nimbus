package com.nimbus.netdisk.model.vo;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 文件夹树节点
 */
@Data
public class FolderTreeVO {

    /** 文件夹 id */
    private Long id;

    /** 父文件夹 id */
    private Long parentId;

    /** 文件夹名称 */
    private String folderName;

    /** 子文件夹列表 */
    private List<FolderTreeVO> children = new ArrayList<>();
}