package com.nimbus.netdisk.model.dto;

import com.nimbus.common.model.PageQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 文件列表/搜索查询条件
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class FileQueryDTO extends PageQuery {

    /** 所属文件夹 id, 为空时查询全部 */
    private Long folderId;

    /** 文件名关键字, 模糊匹配 */
    private String keyword;

    /** 文件分类过滤: IMAGE/VIDEO/AUDIO/DOCUMENT/ARCHIVE/CODE/OTHER */
    private String fileType;

    /** 是否仅收藏: 0否 1是 */
    private Integer isStarred;

    /** 排序字段: name 文件名 | time 修改时间(默认) | size 大小 */
    private String sortKey;

    /** 排序方向: asc 升序 | desc 降序(默认) */
    private String order;
}