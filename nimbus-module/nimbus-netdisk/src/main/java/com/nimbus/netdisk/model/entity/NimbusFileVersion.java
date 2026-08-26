package com.nimbus.netdisk.model.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.nimbus.mybatis.model.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 文件版本表, 记录文件每次内容变更的历史
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("nimbus_file_version")
public class NimbusFileVersion extends BaseEntity {

    /** 文件 id */
    private Long fileId;

    /** 版本号, 文件当前版本为 N, 旧版本为 1..N */
    private Integer versionNo;

    /** 文件大小(bytes) */
    private Long fileSize;

    /** 内容 SHA-256 */
    private String fileHash;

    /** 存储对象 key, 不对外暴露 */
    @JsonIgnore
    private String storageKey;

    /** 操作人用户 id */
    private Long operatorId;

    /** 版本备注 */
    private String remark;
}