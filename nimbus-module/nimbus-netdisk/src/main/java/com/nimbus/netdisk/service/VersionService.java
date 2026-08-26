package com.nimbus.netdisk.service;

import com.nimbus.netdisk.model.entity.NimbusFile;
import com.nimbus.netdisk.model.entity.NimbusFileVersion;

import java.util.List;

/**
 * 文件版本业务接口
 */
public interface VersionService {

    /** 版本列表, 按版本号倒序 */
    List<NimbusFileVersion> list(Long userId, Long fileId);

    /** 提交新版本: 当前内容压入版本链, 文件切换到新内容, 返回更新后的文件 */
    NimbusFile commitNewVersion(Long userId, Long fileId, String storageKey, long fileSize, String fileHash);

    /** 回滚到指定版本, 当前内容自动压入版本链防止丢失 */
    NimbusFile rollback(Long userId, Long fileId, Long versionId);

    /** 文件彻底删除时清理全部版本 */
    void removeAll(Long fileId);
}