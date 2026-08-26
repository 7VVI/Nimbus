package com.nimbus.netdisk.model.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.nimbus.mybatis.model.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 文件表, storageKey 指向存储服务中的对象
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("nimbus_file")
public class NimbusFile extends BaseEntity {

    /** 所属用户 id */
    private Long userId;

    /** 所属文件夹 id, 根为 0 */
    private Long folderId;

    /** 文件名(含扩展名) */
    private String fileName;

    /** 扩展名(小写, 不含点) */
    private String fileExt;

    /** 文件大小(bytes) */
    private Long fileSize;

    /** 内容 SHA-256, 用于秒传 */
    private String fileHash;

    /** 存储对象 key, 不对外暴露 */
    @JsonIgnore
    private String storageKey;

    /** MIME 类型 */
    private String mimeType;

    /** 状态: 1正常 2回收站 3已彻底删除 */
    private Integer status;

    /** 是否收藏: 0否 1是 */
    private Integer isStarred;

    /** 当前版本号, 从 1 开始 */
    private Integer version;

    /** 删除时间(回收站) */
    private LocalDateTime deleteTime;
}