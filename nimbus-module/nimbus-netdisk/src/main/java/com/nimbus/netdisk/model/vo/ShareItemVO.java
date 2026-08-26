package com.nimbus.netdisk.model.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 分享目标条目(文件或文件夹的轻量信息)
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ShareItemVO {

    /** 目标类型: 1文件 2文件夹 */
    private Integer targetType;

    /** 目标 id */
    private Long id;

    /** 名称 */
    private String name;

    /** 扩展名(文件) */
    private String fileExt;

    /** 大小(文件, bytes) */
    private Long fileSize;

    /** 更新时间 */
    private String updateTime;
}