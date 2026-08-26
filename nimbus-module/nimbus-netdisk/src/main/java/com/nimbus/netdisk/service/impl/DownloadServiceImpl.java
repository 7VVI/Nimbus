package com.nimbus.netdisk.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.nimbus.netdisk.constant.NetdiskConstants;
import com.nimbus.netdisk.mapper.NimbusFileMapper;
import com.nimbus.netdisk.mapper.NimbusFolderMapper;
import com.nimbus.netdisk.model.dto.BatchDownloadDTO;
import com.nimbus.netdisk.model.entity.NimbusFile;
import com.nimbus.netdisk.model.entity.NimbusFolder;
import com.nimbus.netdisk.service.DownloadService;
import com.nimbus.netdisk.service.FileService;
import com.nimbus.netdisk.service.FolderService;
import com.nimbus.storage.core.StorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 下载业务实现: 单文件直链/流式 + 批量打包(文件夹展开为相对路径)
 */
@Service
@RequiredArgsConstructor
public class DownloadServiceImpl implements DownloadService {

    private final StorageService storageService;

    private final FileService fileService;

    private final FolderService folderService;

    private final NimbusFileMapper nimbusFileMapper;

    private final NimbusFolderMapper nimbusFolderMapper;

    @Override
    public NimbusFile getOwnedFile(Long userId, Long fileId) {
        return fileService.getOwnedFile(userId, fileId, NetdiskConstants.FILE_STATUS_NORMAL);
    }

    @Override
    public InputStream openFile(NimbusFile file) {
        return storageService.open(file.getStorageKey());
    }

    @Override
    public String accessUrl(NimbusFile file, boolean inline) {
        return storageService.accessUrl(file.getStorageKey(), file.getFileName(), inline);
    }

    @Override
    public LinkedHashMap<String, NimbusFile> collectBatchFiles(Long userId, BatchDownloadDTO dto) {
        LinkedHashMap<String, NimbusFile> result = new LinkedHashMap<>();
        if (dto == null) {
            return result;
        }
        // 单文件: 按文件名去重
        if (dto.getFileIds() != null) {
            for (Long fileId : dto.getFileIds()) {
                NimbusFile file = fileService.getOwnedFile(userId, fileId, NetdiskConstants.FILE_STATUS_NORMAL);
                result.putIfAbsent(file.getFileName(), file);
            }
        }
        // 文件夹: 展开为 "文件夹链/文件名" 相对路径
        if (dto.getFolderIds() != null) {
            Map<Long, NimbusFolder> folders = folderIndex(userId);
            for (Long folderId : dto.getFolderIds()) {
                for (Long id : folderService.listSubtreeIds(userId, folderId)) {
                    List<NimbusFile> files = nimbusFileMapper.selectList(new LambdaQueryWrapper<NimbusFile>()
                        .eq(NimbusFile::getUserId, userId)
                        .eq(NimbusFile::getFolderId, id)
                        .eq(NimbusFile::getStatus, NetdiskConstants.FILE_STATUS_NORMAL));
                    for (NimbusFile file : files) {
                        String relative = buildRelativePath(folders, id, file.getFileName());
                        result.putIfAbsent(relative, file);
                    }
                }
            }
        }
        return result;
    }

    /** 构建 文件夹链/文件名, 如 资料/会议/纪要.pdf */
    private String buildRelativePath(Map<Long, NimbusFolder> folders, Long folderId, String fileName) {
        List<String> names = new ArrayList<>();
        Long current = folderId;
        while (current != null && current != NetdiskConstants.ROOT_FOLDER_ID) {
            NimbusFolder folder = folders.get(current);
            if (folder == null) {
                break;
            }
            names.add(0, folder.getFolderName());
            current = folder.getParentId();
        }
        if (names.isEmpty()) {
            return fileName;
        }
        return String.join("/", names) + "/" + fileName;
    }

    private Map<Long, NimbusFolder> folderIndex(Long userId) {
        Map<Long, NimbusFolder> map = new HashMap<>();
        nimbusFolderMapper.selectList(new LambdaQueryWrapper<NimbusFolder>()
                .eq(NimbusFolder::getUserId, userId)
                .eq(NimbusFolder::getStatus, NetdiskConstants.FOLDER_STATUS_NORMAL))
            .forEach(folder -> map.put(folder.getId(), folder));
        return map;
    }
}