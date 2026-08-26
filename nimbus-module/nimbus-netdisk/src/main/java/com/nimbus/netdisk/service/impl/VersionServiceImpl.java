package com.nimbus.netdisk.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.nimbus.common.exception.BusinessException;
import com.nimbus.common.exception.ErrorCode;
import com.nimbus.netdisk.constant.NetdiskConstants;
import com.nimbus.netdisk.mapper.NimbusFileMapper;
import com.nimbus.netdisk.mapper.NimbusFileVersionMapper;
import com.nimbus.netdisk.model.entity.NimbusFile;
import com.nimbus.netdisk.model.entity.NimbusFileVersion;
import com.nimbus.netdisk.service.FileService;
import com.nimbus.netdisk.service.VersionService;
import com.nimbus.system.service.QuotaService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 文件版本业务实现: 当前内容压链 -> 切换新内容 -> 配额差额调整
 */
@Service
@RequiredArgsConstructor
public class VersionServiceImpl implements VersionService {

    private final NimbusFileVersionMapper nimbusFileVersionMapper;

    private final NimbusFileMapper nimbusFileMapper;

    private final FileService fileService;

    private final QuotaService quotaService;

    @Override
    public List<NimbusFileVersion> list(Long userId, Long fileId) {
        fileService.getOwnedFile(userId, fileId, NetdiskConstants.FILE_STATUS_NORMAL);
        return nimbusFileVersionMapper.selectList(new LambdaQueryWrapper<NimbusFileVersion>()
            .eq(NimbusFileVersion::getFileId, fileId)
            .orderByDesc(NimbusFileVersion::getVersionNo));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public NimbusFile commitNewVersion(Long userId, Long fileId, String storageKey, long fileSize, String fileHash) {
        NimbusFile file = fileService.getOwnedFile(userId, fileId, NetdiskConstants.FILE_STATUS_NORMAL);
        adjustQuota(file.getUserId(), fileSize - file.getFileSize());

        // 当前内容压入版本链
        NimbusFileVersion version = new NimbusFileVersion();
        version.setFileId(fileId);
        version.setVersionNo(file.getVersion());
        version.setFileSize(file.getFileSize());
        version.setFileHash(file.getFileHash());
        version.setStorageKey(file.getStorageKey());
        version.setOperatorId(userId);
        nimbusFileVersionMapper.insert(version);

        NimbusFile update = new NimbusFile();
        update.setId(fileId);
        update.setStorageKey(storageKey);
        update.setFileSize(fileSize);
        update.setFileHash(fileHash.toLowerCase());
        update.setVersion(file.getVersion() + 1);
        nimbusFileMapper.updateById(update);
        return nimbusFileMapper.selectById(fileId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public NimbusFile rollback(Long userId, Long fileId, Long versionId) {
        NimbusFile file = fileService.getOwnedFile(userId, fileId, NetdiskConstants.FILE_STATUS_NORMAL);
        NimbusFileVersion version = nimbusFileVersionMapper.selectOne(new LambdaQueryWrapper<NimbusFileVersion>()
            .eq(NimbusFileVersion::getId, versionId)
            .eq(NimbusFileVersion::getFileId, fileId));
        if (version == null) {
            throw new BusinessException(ErrorCode.DATA_NOT_EXIST, "版本不存在: " + versionId);
        }
        adjustQuota(file.getUserId(), version.getFileSize() - file.getFileSize());

        // 当前内容压链, 保证回滚可逆
        NimbusFileVersion current = new NimbusFileVersion();
        current.setFileId(fileId);
        current.setVersionNo(file.getVersion());
        current.setFileSize(file.getFileSize());
        current.setFileHash(file.getFileHash());
        current.setStorageKey(file.getStorageKey());
        current.setOperatorId(userId);
        current.setRemark("回滚前内容");
        nimbusFileVersionMapper.insert(current);

        NimbusFile update = new NimbusFile();
        update.setId(fileId);
        update.setStorageKey(version.getStorageKey());
        update.setFileSize(version.getFileSize());
        update.setFileHash(version.getFileHash());
        update.setVersion(file.getVersion() + 1);
        nimbusFileMapper.updateById(update);
        return nimbusFileMapper.selectById(fileId);
    }

    @Override
    public void removeAll(Long fileId) {
        nimbusFileVersionMapper.delete(new LambdaQueryWrapper<NimbusFileVersion>()
            .eq(NimbusFileVersion::getFileId, fileId));
    }

    /** 配额差额调整, 增加部分先做可用性检查 */
    private void adjustQuota(Long userId, long delta) {
        if (delta > 0) {
            quotaService.checkQuota(userId, delta);
        }
        if (delta != 0) {
            quotaService.changeUsage(userId, delta);
        }
    }
}