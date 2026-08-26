package com.nimbus.netdisk.model.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 面包屑节点
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class BreadcrumbVO {

    /** 文件夹 id, 根为 0 */
    private Long id;

    /** 文件夹名称 */
    private String name;
}