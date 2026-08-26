package com.nimbus.netdisk.model.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.nimbus.mybatis.model.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 文件夹表, folderPath 为物化路径(如 /1/5/), 用于子树查询与面包屑
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("nimbus_folder")
public class NimbusFolder extends BaseEntity {

    /** 所属用户 id */
    private Long userId;

    /** 父文件夹 id, 根为 0 */
    private Long parentId;

    /** 文件夹名称 */
    private String folderName;

    /** 物化路径, 如 /1/5/ */
    private String folderPath;

    /** 层级深度, 根下第一层为 1 */
    private Integer depth;

    /** 状态: 1正常 2回收站 */
    private Integer status;

    /** 删除时间(回收站) */
    private LocalDateTime deleteTime;
}