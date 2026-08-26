package com.nimbus.netdisk.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.nimbus.common.model.PageQuery;
import com.nimbus.common.model.PageResult;
import com.nimbus.netdisk.constant.NetdiskConstants;
import com.nimbus.netdisk.mapper.NimbusFileMapper;
import com.nimbus.netdisk.mapper.NimbusFolderMapper;
import com.nimbus.netdisk.model.entity.NimbusFile;
import com.nimbus.netdisk.model.entity.NimbusFolder;
import com.nimbus.netdisk.model.vo.RecycleItemVO;
import com.nimbus.netdisk.service.FileService;
import com.nimbus.netdisk.service.FolderService;
import com.nimbus.netdisk.service.RecycleService;
import com.nimbus.netdisk.service.VersionService;
import com.nimbus.storage.core.StorageService;
import com.nimbus.system.service.QuotaService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * 回收站业务实现: 软删除 -> 恢复(原位置校验) / 彻底删除(释放存储与配额)
 */
@Service
@RequiredArgsConstructor
public class RecycleServiceImpl implements RecycleService {

    /** 回收站单次加载上限, 防止超大回收站拖垮内存 */
    private static final int RECYCLE_LOAD_LIMIT = 2000;

    private final NimbusFileMapper nimbusFileMapper;

    private final NimbusFolderMapper nimbusFolderMapper;

    private final StorageService storageService;

    private final QuotaService quotaService;

    private final FileService fileService;

    private final FolderService folderService;

    private final VersionService versionService;

    @Override
    public PageResult<RecycleItemVO> page(Long userId, PageQuery query) {
        List<NimbusFile> files = nimbusFileMapper.selectList(new LambdaQueryWrapper<NimbusFile>()
            .eq(NimbusFile::getUserId, userId)
            .eq(NimbusFile::getStatus, NetdiskConstants.FILE_STATUS_RECYCLED)
            .orderByDesc(NimbusFile::getDeleteTime)
            .last("LIMIT " + RECYCLE_LOAD_LIMIT));
        List<NimbusFolder> folders = nimbusFolderMapper.selectList(new LambdaQueryWrapper<NimbusFolder>()
            .eq(NimbusFolder::getUserId, userId)
            .eq(NimbusFolder::getStatus, NetdiskConstants.FOLDER_STATUS_RECYCLED)
            .orderByDesc(NimbusFolder::getDeleteTime)
            .last("LIMIT " + RECYCLE_LOAD_LIMIT));
        List<RecycleItemVO> items = new ArrayList<>();
        files.forEach(file -> items.add(new RecycleItemVO(NetdiskConstants.TARGET_TYPE_FILE, file.getId(),
            file.getFileName(), file.getFileExt(), file.getFileSize(), file.getDeleteTime())));
        folders.forEach(folder -> items.add(new RecycleItemVO(NetdiskConstants.TARGET_TYPE_FOLDER, folder.getId(),
            folder.getFolderName(), null, null, folder.getDeleteTime())));
        items.sort(Comparator.comparing(RecycleItemVO::getDeleteTime,
            Comparator.nullsLast(Comparator.reverseOrder())));
        // 统一分页切片
        int total = items.size();
        int from = (query.getPageNum() - 1) * query.getPageSize();
        if (from >= total) {
            return PageResult.empty();
        }
        int to = Math.min(from + query.getPageSize(), total);
        return PageResult.of(total, items.subList(from, to));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void restore(Long userId, Integer targetType, Long id) {
        if (Objects.equals(targetType, NetdiskConstants.TARGET_TYPE_FOLDER)) {
            restoreFolder(userId, id);
        } else {
            restoreFile(userId, id);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void purge(Long userId, Integer targetType, Long id) {
        if (Objects.equals(targetType, NetdiskConstants.TARGET_TYPE_FOLDER)) {
            purgeFolder(userId, id, new HashSet<>());
        } else {
            purgeFile(userId, id, new HashSet<>());
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public long clean(Long userId) {
        Set<Long> purgedFiles = new HashSet<>();
        // 先清文件夹子树, 再清游离文件, 避免重复释放配额
        List<NimbusFolder> folders = nimbusFolderMapper.selectList(new LambdaQueryWrapper<NimbusFolder>()
            .eq(NimbusFolder::getUserId, userId)
            .eq(NimbusFolder::getStatus, NetdiskConstants.FOLDER_STATUS_RECYCLED));
        for (NimbusFolder folder : folders) {
            purgeFolder(userId, folder.getId(), purgedFiles);
        }
        List<NimbusFile> files = nimbusFileMapper.selectList(new LambdaQueryWrapper<NimbusFile>()
            .eq(NimbusFile::getUserId, userId)
            .eq(NimbusFile::getStatus, NetdiskConstants.FILE_STATUS_RECYCLED));
        for (NimbusFile file : files) {
            if (!purgedFiles.contains(file.getId())) {
                purgeFileInternal(userId, file, purgedFiles);
            }
        }
        return purgedFiles.size();
    }

    /** 恢复文件, 原文件夹不可用时回退根目录 */
    private void restoreFile(Long userId, Long fileId) {
        NimbusFile file = fileService.getOwnedFile(userId, fileId, NetdiskConstants.FILE_STATUS_RECYCLED);
        Long folderId = resolveRestoreFolder(userId, file.getFolderId());
        NimbusFile update = new NimbusFile();
        update.setId(file.getId());
        update.setFolderId(folderId);
        update.setStatus(NetdiskConstants.FILE_STATUS_NORMAL);
        update.setDeleteTime(null);
        nimbusFileMapper.updateById(update);
    }

    /** 恢复文件夹(含子树路径重算)与其中文件 */
    private void restoreFolder(Long userId, Long folderId) {
        NimbusFolder folder = folderService.getOwnedFolder(userId, folderId, NetdiskConstants.FOLDER_STATUS_RECYCLED);
        Long parentId = resolveRestoreFolder(userId, folder.getParentId());
        NimbusFolder parent = parentId == NetdiskConstants.ROOT_FOLDER_ID
            ? null : folderService.getOwnedFolder(userId, parentId, NetdiskConstants.FOLDER_STATUS_NORMAL);
        NimbusFolder update = new NimbusFolder();
        update.setId(folder.getId());
        update.setParentId(parentId);
        update.setStatus(NetdiskConstants.FOLDER_STATUS_NORMAL);
        update.setDeleteTime(null);
        nimbusFolderMapper.updateById(update);
        // 子树路径重算后, 子文件随所在文件夹恢复
        folderService.recomputeSubtreePaths(userId, folder, parent);
        List<Long> folderIds = folderService.listSubtreeIds(userId, folderId);
        nimbusFileMapper.update(null, new LambdaUpdateWrapper<NimbusFile>()
            .eq(NimbusFile::getUserId, userId)
            .in(NimbusFile::getFolderId, folderIds)
            .set(NimbusFile::getStatus, NetdiskConstants.FILE_STATUS_NORMAL)
            .set(NimbusFile::getDeleteTime, null));
    }

    /** 原位置恢复校验: 父级存在且正常则原样恢复, 否则回退根目录 */
    private Long resolveRestoreFolder(Long userId, Long parentId) {
        if (parentId == null || parentId == NetdiskConstants.ROOT_FOLDER_ID) {
            return NetdiskConstants.ROOT_FOLDER_ID;
        }
        NimbusFolder parent = nimbusFolderMapper.selectOne(new LambdaQueryWrapper<NimbusFolder>()
            .eq(NimbusFolder::getId, parentId)
            .eq(NimbusFolder::getUserId, userId)
            .eq(NimbusFolder::getStatus, NetdiskConstants.FOLDER_STATUS_NORMAL));
        return parent == null ? NetdiskConstants.ROOT_FOLDER_ID : parentId;
    }

    private void purgeFile(Long userId, Long fileId, Set<Long> purgedFiles) {
        NimbusFile file = fileService.getOwnedFile(userId, fileId, NetdiskConstants.FILE_STATUS_RECYCLED);
        purgeFileInternal(userId, file, purgedFiles);
    }

    /** 彻底删除单个文件: 无引用时删存储对象, 释放配额, 清理版本 */
    private void purgeFileInternal(Long userId, NimbusFile file, Set<Long> purgedFiles) {
        if (!purgedFiles.add(file.getId())) {
            return;
        }
        deleteStorageIfUnused(file);
        quotaService.changeUsage(userId, -file.getFileSize());
        versionService.removeAll(file.getId());
        nimbusFileMapper.deleteById(file.getId());
    }

    /** 删除文件夹子树: 子文件逐个彻底删除, 最后删除文件夹记录 */
    private void purgeFolder(Long userId, Long folderId, Set<Long> purgedFiles) {
        NimbusFolder folder = folderService.getOwnedFolder(userId, folderId, NetdiskConstants.FOLDER_STATUS_RECYCLED);
        List<Long> folderIds = folderService.listSubtreeIds(userId, folderId);
        List<NimbusFile> files = nimbusFileMapper.selectList(new LambdaQueryWrapper<NimbusFile>()
            .eq(NimbusFile::getUserId, userId)
            .eq(NimbusFile::getStatus, NetdiskConstants.FILE_STATUS_RECYCLED)
            .in(NimbusFile::getFolderId, folderIds));
        for (NimbusFile file : files) {
            purgeFileInternal(userId, file, purgedFiles);
        }
        for (Long id : folderIds) {
            nimbusFolderMapper.deleteById(id);
        }
    }

    /** 存储对象无任何文件引用时删除, 秒传/复制共享对象不受影响 */
    private void deleteStorageIfUnused(NimbusFile file) {
        Long count = nimbusFileMapper.selectCount(new LambdaQueryWrapper<NimbusFile>()
            .eq(NimbusFile::getStorageKey, file.getStorageKey())
            .ne(NimbusFile::getId, file.getId())
            .ne(NimbusFile::getStatus, NetdiskConstants.FILE_STATUS_DELETED));
        if (count == 0 && storageService.exists(file.getStorageKey())) {
            storageService.delete(file.getStorageKey());
        }
    }
}