package com.nimbus.netdisk.model.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 回收站条目(文件与文件夹统一视图)
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RecycleItemVO {

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

    /** 删除时间 */
    private LocalDateTime deleteTime;
}