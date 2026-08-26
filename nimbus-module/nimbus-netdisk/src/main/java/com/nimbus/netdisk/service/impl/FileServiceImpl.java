package com.nimbus.netdisk.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.nimbus.common.exception.BusinessException;
import com.nimbus.common.exception.ErrorCode;
import com.nimbus.common.model.PageResult;
import com.nimbus.mybatis.utils.PageUtils;
import com.nimbus.netdisk.constant.NetdiskConstants;
import com.nimbus.netdisk.mapper.NimbusFileMapper;
import com.nimbus.netdisk.model.dto.FileCopyDTO;
import com.nimbus.netdisk.model.dto.FileMoveDTO;
import com.nimbus.netdisk.model.dto.FileQueryDTO;
import com.nimbus.netdisk.model.dto.FileRenameDTO;
import com.nimbus.netdisk.model.entity.NimbusFile;
import com.nimbus.netdisk.service.FileService;
import com.nimbus.netdisk.service.FolderService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 文件业务实现: 列表/搜索/重命名/移动/复制/收藏, 复制与秒传共享同一存储对象
 */
@Service
@RequiredArgsConstructor
public class FileServiceImpl implements FileService {

    private static final int RECENT_LIMIT_MAX = 100;

    /** 文件分类 -> 扩展名集合 */
    private static final Map<String, List<String>> CATEGORY_EXTENSIONS = Map.of(
        NetdiskConstants.CATEGORY_IMAGE, List.of("jpg", "jpeg", "png", "gif", "bmp", "webp", "svg", "ico"),
        NetdiskConstants.CATEGORY_VIDEO, List.of("mp4", "mkv", "avi", "mov", "wmv", "flv", "webm", "m4v"),
        NetdiskConstants.CATEGORY_AUDIO, List.of("mp3", "wav", "flac", "aac", "ogg", "m4a", "wma"),
        NetdiskConstants.CATEGORY_DOCUMENT, List.of("doc", "docx", "xls", "xlsx", "ppt", "pptx", "pdf", "txt", "md", "csv"),
        NetdiskConstants.CATEGORY_ARCHIVE, List.of("zip", "rar", "7z", "tar", "gz"),
        NetdiskConstants.CATEGORY_CODE, List.of("java", "py", "js", "ts", "c", "cpp", "go", "html", "css", "json", "xml", "sql", "yml", "yaml", "sh"));

    private final NimbusFileMapper nimbusFileMapper;

    private final FolderService folderService;

    @Override
    public PageResult<NimbusFile> page(Long userId, FileQueryDTO query) {
        LambdaQueryWrapper<NimbusFile> wrapper = new LambdaQueryWrapper<NimbusFile>()
            .eq(NimbusFile::getUserId, userId)
            .eq(NimbusFile::getStatus, NetdiskConstants.FILE_STATUS_NORMAL)
            .eq(query.getFolderId() != null, NimbusFile::getFolderId, query.getFolderId())
            .eq(query.getIsStarred() != null, NimbusFile::getIsStarred, query.getIsStarred())
            .like(StringUtils.hasText(query.getKeyword()), NimbusFile::getFileName, query.getKeyword());
        if (StringUtils.hasText(query.getFileType())) {
            List<String> extensions = extensionsOf(query.getFileType());
            if (!extensions.isEmpty()) {
                wrapper.in(NimbusFile::getFileExt, extensions);
            }
        }
        appendOrder(wrapper, query.getSortKey(), query.getOrder());
        Page<NimbusFile> page = nimbusFileMapper.selectPage(PageUtils.toPage(query), wrapper);
        return PageUtils.toResult(page);
    }

    @Override
    public List<NimbusFile> recent(Long userId, int limit) {
        int size = Math.min(Math.max(limit, 1), RECENT_LIMIT_MAX);
        return nimbusFileMapper.selectList(new LambdaQueryWrapper<NimbusFile>()
            .eq(NimbusFile::getUserId, userId)
            .eq(NimbusFile::getStatus, NetdiskConstants.FILE_STATUS_NORMAL)
            .orderByDesc(NimbusFile::getUpdateTime)
            .last("LIMIT " + size));
    }

    @Override
    public List<NimbusFile> starred(Long userId) {
        return nimbusFileMapper.selectList(new LambdaQueryWrapper<NimbusFile>()
            .eq(NimbusFile::getUserId, userId)
            .eq(NimbusFile::getStatus, NetdiskConstants.FILE_STATUS_NORMAL)
            .eq(NimbusFile::getIsStarred, NetdiskConstants.STAR_YES)
            .orderByDesc(NimbusFile::getUpdateTime));
    }

    @Override
    public NimbusFile detail(Long userId, Long fileId) {
        return getOwnedFile(userId, fileId, NetdiskConstants.FILE_STATUS_NORMAL);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void rename(Long userId, Long fileId, FileRenameDTO dto) {
        NimbusFile file = getOwnedFile(userId, fileId, NetdiskConstants.FILE_STATUS_NORMAL);
        folderService.checkNameAvailable(userId, file.getFolderId(), dto.getFileName(), null, file.getId());
        NimbusFile update = new NimbusFile();
        update.setId(file.getId());
        update.setFileName(dto.getFileName());
        update.setFileExt(extractExt(dto.getFileName()));
        nimbusFileMapper.updateById(update);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void move(Long userId, Long fileId, FileMoveDTO dto) {
        NimbusFile file = getOwnedFile(userId, fileId, NetdiskConstants.FILE_STATUS_NORMAL);
        Long folderId = dto.getFolderId() == null ? NetdiskConstants.ROOT_FOLDER_ID : dto.getFolderId();
        // 目标文件夹校验(0 或用户自己的正常文件夹)
        if (folderId != NetdiskConstants.ROOT_FOLDER_ID) {
            folderService.getOwnedFolder(userId, folderId, NetdiskConstants.FOLDER_STATUS_NORMAL);
        }
        folderService.checkNameAvailable(userId, folderId, file.getFileName(), null, file.getId());
        NimbusFile update = new NimbusFile();
        update.setId(file.getId());
        update.setFolderId(folderId);
        nimbusFileMapper.updateById(update);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public NimbusFile copy(Long userId, Long fileId, FileCopyDTO dto) {
        NimbusFile file = getOwnedFile(userId, fileId, NetdiskConstants.FILE_STATUS_NORMAL);
        Long folderId = dto.getFolderId() == null ? NetdiskConstants.ROOT_FOLDER_ID : dto.getFolderId();
        if (folderId != NetdiskConstants.ROOT_FOLDER_ID) {
            folderService.getOwnedFolder(userId, folderId, NetdiskConstants.FOLDER_STATUS_NORMAL);
        }
        String fileName = file.getFileName();
        if (folderId.equals(file.getFolderId())) {
            // 同目录复制自动追加序号
            fileName = appendSuffix(fileName);
        }
        folderService.checkNameAvailable(userId, folderId, fileName, null, null);

        NimbusFile copy = new NimbusFile();
        copy.setUserId(userId);
        copy.setFolderId(folderId);
        copy.setFileName(fileName);
        copy.setFileExt(file.getFileExt());
        copy.setFileSize(file.getFileSize());
        copy.setFileHash(file.getFileHash());
        copy.setStorageKey(file.getStorageKey());
        copy.setMimeType(file.getMimeType());
        copy.setStatus(NetdiskConstants.FILE_STATUS_NORMAL);
        copy.setIsStarred(NetdiskConstants.STAR_NO);
        copy.setVersion(1);
        nimbusFileMapper.insert(copy);
        return copy;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void star(Long userId, Long fileId, boolean starred) {
        NimbusFile file = getOwnedFile(userId, fileId, NetdiskConstants.FILE_STATUS_NORMAL);
        // 指定列更新: 收藏属于元数据, 不触发 updateTime 填充, 避免影响按修改时间的排序
        nimbusFileMapper.update(null, new LambdaUpdateWrapper<NimbusFile>()
            .eq(NimbusFile::getId, file.getId())
            .set(NimbusFile::getIsStarred, starred ? NetdiskConstants.STAR_YES : NetdiskConstants.STAR_NO));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteToRecycle(Long userId, Long fileId) {
        NimbusFile file = getOwnedFile(userId, fileId, NetdiskConstants.FILE_STATUS_NORMAL);
        NimbusFile update = new NimbusFile();
        update.setId(file.getId());
        update.setStatus(NetdiskConstants.FILE_STATUS_RECYCLED);
        update.setDeleteTime(java.time.LocalDateTime.now());
        nimbusFileMapper.updateById(update);
    }

    @Override
    public NimbusFile getOwnedFile(Long userId, Long fileId, Integer status) {
        LambdaQueryWrapper<NimbusFile> wrapper = new LambdaQueryWrapper<NimbusFile>()
            .eq(NimbusFile::getId, fileId)
            .eq(NimbusFile::getUserId, userId)
            .eq(status != null, NimbusFile::getStatus, status);
        NimbusFile file = nimbusFileMapper.selectOne(wrapper);
        if (file == null) {
            throw new BusinessException(ErrorCode.FILE_NOT_EXIST, "文件不存在或已删除: " + fileId);
        }
        return file;
    }

    @Override
    public String categoryOf(String fileExt) {
        if (fileExt == null || fileExt.isBlank()) {
            return NetdiskConstants.CATEGORY_OTHER;
        }
        return CATEGORY_EXTENSIONS.entrySet().stream()
            .filter(entry -> entry.getValue().contains(fileExt.toLowerCase(Locale.ROOT)))
            .map(Map.Entry::getKey)
            .findFirst()
            .orElse(NetdiskConstants.CATEGORY_OTHER);
    }

    @Override
    public List<String> extensionsOf(String category) {
        if (category == null) {
            return List.of();
        }
        return CATEGORY_EXTENSIONS.getOrDefault(category.toUpperCase(Locale.ROOT), List.of());
    }

    private void appendOrder(LambdaQueryWrapper<NimbusFile> wrapper, String sortKey, String order) {
        boolean asc = "asc".equalsIgnoreCase(order);
        String key = sortKey == null ? "time" : sortKey;
        switch (key) {
            case "name" -> {
                if (asc) {
                    wrapper.orderByAsc(NimbusFile::getFileName);
                } else {
                    wrapper.orderByDesc(NimbusFile::getFileName);
                }
            }
            case "size" -> {
                if (asc) {
                    wrapper.orderByAsc(NimbusFile::getFileSize);
                } else {
                    wrapper.orderByDesc(NimbusFile::getFileSize);
                }
            }
            default -> {
                if (asc) {
                    wrapper.orderByAsc(NimbusFile::getUpdateTime);
                } else {
                    wrapper.orderByDesc(NimbusFile::getUpdateTime);
                }
            }
        }
    }

    /** 提取小写扩展名(不含点), 无扩展名返回 null */
    public static String extractExt(String fileName) {
        if (fileName == null || fileName.isBlank()) {
            return null;
        }
        int dot = fileName.lastIndexOf('.');
        if (dot <= 0 || dot == fileName.length() - 1) {
            return null;
        }
        return fileName.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    /** 同目录复制追加序号: name.ext -> name (1).ext */
    private String appendSuffix(String fileName) {
        int dot = fileName.lastIndexOf('.');
        if (dot <= 0) {
            return fileName + " (1)";
        }
        return fileName.substring(0, dot) + " (1)" + fileName.substring(dot);
    }
}