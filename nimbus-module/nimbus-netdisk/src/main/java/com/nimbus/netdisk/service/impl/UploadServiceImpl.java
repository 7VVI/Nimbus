package com.nimbus.netdisk.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.nimbus.common.exception.BusinessException;
import com.nimbus.common.exception.ErrorCode;
import com.nimbus.netdisk.constant.NetdiskConstants;
import com.nimbus.netdisk.mapper.NimbusFileMapper;
import com.nimbus.netdisk.model.dto.UploadInitDTO;
import com.nimbus.netdisk.model.dto.UploadMergeDTO;
import com.nimbus.netdisk.model.entity.NimbusFile;
import com.nimbus.netdisk.model.vo.UploadInitVO;
import com.nimbus.netdisk.service.FileService;
import com.nimbus.netdisk.service.FolderService;
import com.nimbus.netdisk.service.UploadService;
import com.nimbus.netdisk.service.VersionService;
import com.nimbus.redisson.utils.RedisUtils;
import com.nimbus.storage.core.StorageService;
import com.nimbus.system.service.QuotaService;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * 上传业务实现
 * <ul>
 *     <li>秒传: SHA-256 命中已有对象, 直接创建引用记录</li>
 *     <li>分片: 任务与分片位图存 Redis, 支持断点续传</li>
 *     <li>版本上传: fileId 非空时合并后走版本链</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class UploadServiceImpl implements UploadService {

    /** 分片大小: 5MB */
    public static final long CHUNK_SIZE = 5 * 1024 * 1024L;

    private static final Duration TASK_TTL = Duration.ofHours(24);
    private static final Duration CHUNK_TTL = Duration.ofHours(24);
    private static final Duration HASH_TTL = Duration.ofDays(30);

    private static final String TASK_KEY_PREFIX = "nimbus:upload:task:";
    private static final String CHUNK_KEY_PREFIX = "nimbus:upload:chunk:";
    private static final String HASH_KEY_PREFIX = "netdisk_file_hash:";

    private static final DateTimeFormatter KEY_DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd");

    private static final Map<String, String> MIME_MAP = Map.ofEntries(
        Map.entry("jpg", "image/jpeg"), Map.entry("jpeg", "image/jpeg"), Map.entry("png", "image/png"),
        Map.entry("gif", "image/gif"), Map.entry("webp", "image/webp"), Map.entry("svg", "image/svg+xml"),
        Map.entry("mp4", "video/mp4"), Map.entry("mkv", "video/x-matroska"), Map.entry("mov", "video/quicktime"),
        Map.entry("webm", "video/webm"), Map.entry("mp3", "audio/mpeg"), Map.entry("wav", "audio/wav"),
        Map.entry("flac", "audio/flac"), Map.entry("pdf", "application/pdf"),
        Map.entry("doc", "application/msword"), Map.entry("docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document"),
        Map.entry("xls", "application/vnd.ms-excel"), Map.entry("xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"),
        Map.entry("ppt", "application/vnd.ms-powerpoint"), Map.entry("pptx", "application/vnd.openxmlformats-officedocument.presentationml.presentation"),
        Map.entry("zip", "application/zip"), Map.entry("rar", "application/vnd.rar"), Map.entry("7z", "application/x-7z-compressed"),
        Map.entry("txt", "text/plain"), Map.entry("md", "text/markdown"), Map.entry("csv", "text/csv"),
        Map.entry("json", "application/json"), Map.entry("xml", "application/xml"),
        Map.entry("html", "text/html"), Map.entry("css", "text/css"), Map.entry("js", "text/javascript"));

    private final NimbusFileMapper nimbusFileMapper;

    private final StorageService storageService;

    private final RedisUtils redisUtils;

    private final QuotaService quotaService;

    private final FolderService folderService;

    private final FileService fileService;

    private final VersionService versionService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public UploadInitVO init(Long userId, UploadInitDTO dto) {
        Long folderId = dto.getFolderId() == null ? NetdiskConstants.ROOT_FOLDER_ID : dto.getFolderId();
        validateTarget(userId, folderId, dto.getFileId());
        quotaService.checkQuota(userId, dto.getFileSize());

        // 秒传: 哈希命中已有对象(content 相同, 存储零拷贝)
        String existingKey = findExistingStorageKey(dto.getFileHash());
        if (existingKey != null && dto.getFileId() == null) {
            NimbusFile file = createFileRecord(userId, folderId, dto.getFileName(), dto.getFileSize(),
                dto.getFileHash(), existingKey);
            return instantVo(file);
        }
        // 版本上传: 内容一致则拒绝
        if (dto.getFileId() != null) {
            NimbusFile current = fileService.getOwnedFile(userId, dto.getFileId(), NetdiskConstants.FILE_STATUS_NORMAL);
            if (current.getFileHash().equalsIgnoreCase(dto.getFileHash())) {
                throw new BusinessException(ErrorCode.BUSINESS_ERROR, "新版本内容与当前版本一致, 无需上传");
            }
        }

        String uploadId = UUID.randomUUID().toString().replace("-", "");
        int chunkCount = (int) Math.max(1, Math.ceil((double) dto.getFileSize() / CHUNK_SIZE));
        String objectKey = buildObjectKey(userId, dto.getFileName());
        UploadTask task = new UploadTask();
        task.setUploadId(uploadId);
        task.setUserId(userId);
        task.setFileName(dto.getFileName());
        task.setFileExt(FileServiceImpl.extractExt(dto.getFileName()));
        task.setMimeType(mimeOf(dto.getFileName()));
        task.setFileSize(dto.getFileSize());
        task.setFileHash(dto.getFileHash().toLowerCase(Locale.ROOT));
        task.setFolderId(folderId);
        task.setObjectKey(objectKey);
        task.setChunkCount(chunkCount);
        task.setChunkSize(CHUNK_SIZE);
        task.setFileId(dto.getFileId());
        redisUtils.set(TASK_KEY_PREFIX + uploadId, task, TASK_TTL);

        UploadInitVO vo = new UploadInitVO();
        vo.setInstant(false);
        vo.setUploadId(uploadId);
        vo.setChunkCount(chunkCount);
        vo.setChunkSize(CHUNK_SIZE);
        vo.setExistChunks(collectExistChunks(uploadId, chunkCount));
        return vo;
    }

    @Override
    public void uploadChunk(String uploadId, int chunkIndex, byte[] data) {
        UploadTask task = getTask(uploadId);
        if (chunkIndex < 0 || chunkIndex >= task.getChunkCount()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "分片序号越界: " + chunkIndex);
        }
        storageService.putChunk(uploadId, chunkIndex, data);
        // 分片位图, 重复上传直接覆盖
        redisUtils.set(CHUNK_KEY_PREFIX + uploadId + ":" + chunkIndex, 1, CHUNK_TTL);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public NimbusFile merge(Long userId, UploadMergeDTO dto) {
        UploadTask task = getTask(dto.getUploadId());
        if (!task.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "上传任务不属于当前用户");
        }
        Collection<String> chunkKeys = redisUtils.keys(CHUNK_KEY_PREFIX + task.getUploadId() + ":*");
        if (chunkKeys.size() < task.getChunkCount()) {
            throw new BusinessException(ErrorCode.CHUNK_INCOMPLETE,
                "分片未全部上传完成: " + chunkKeys.size() + "/" + task.getChunkCount());
        }
        // 合并失败时任务保留, 客户端可重试(存储层幂等)
        storageService.mergeChunks(task.getUploadId(), task.getChunkCount(), task.getObjectKey());
        cleanTask(task);
        NimbusFile file;
        if (task.getFileId() != null) {
            file = versionService.commitNewVersion(userId, task.getFileId(), task.getObjectKey(),
                task.getFileSize(), task.getFileHash());
        } else {
            file = createFileRecord(userId, task.getFolderId(), task.getFileName(), task.getFileSize(),
                task.getFileHash(), task.getObjectKey());
        }
        // 记录哈希缓存, 供后续秒传
        redisUtils.set(hashCacheKey(task.getFileHash()), task.getObjectKey(), HASH_TTL);
        return file;
    }

    @Override
    public void cancel(String uploadId) {
        UploadTask task = getTask(uploadId);
        storageService.deleteUpload(uploadId);
        cleanTask(task);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public NimbusFile singleUpload(Long userId, MultipartFile file, Long folderId) {
        final String fileName = cleanFileName(file.getOriginalFilename());
        final byte[] data;
        try {
            data = file.getBytes();
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "读取上传内容失败");
        }
        UploadInitDTO dto = new UploadInitDTO();
        dto.setFileName(fileName);
        dto.setFileSize((long) data.length);
        dto.setFileHash(sha256Hex(data));
        dto.setFolderId(folderId);
        UploadInitVO vo = init(userId, dto);
        if (vo.isInstant()) {
            return vo.getFile();
        }
        UploadTask task = getTask(vo.getUploadId());
        if (task.getChunkCount() > 1) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "文件超过 " + CHUNK_SIZE / 1024 / 1024 + "MB, 请使用分片上传");
        }
        uploadChunk(vo.getUploadId(), 0, data);
        UploadMergeDTO mergeDto = new UploadMergeDTO();
        mergeDto.setUploadId(vo.getUploadId());
        return merge(userId, mergeDto);
    }

    /** 校验上传目标(文件夹/版本文件)归属 */
    private void validateTarget(Long userId, Long folderId, Long fileId) {
        if (folderId != NetdiskConstants.ROOT_FOLDER_ID) {
            folderService.getOwnedFolder(userId, folderId, NetdiskConstants.FOLDER_STATUS_NORMAL);
        }
        if (fileId != null) {
            fileService.getOwnedFile(userId, fileId, NetdiskConstants.FILE_STATUS_NORMAL);
        }
    }

    /** 秒传查询: Redis 缓存优先, 未命中回源 DB */
    private String findExistingStorageKey(String fileHash) {
        String hash = fileHash.toLowerCase(Locale.ROOT);
        String cacheKey = hashCacheKey(hash);
        String key = redisUtils.get(cacheKey);
        if (key != null) {
            return key;
        }
        NimbusFile existing = nimbusFileMapper.selectOne(new LambdaQueryWrapper<NimbusFile>()
            .eq(NimbusFile::getFileHash, hash)
            .ne(NimbusFile::getStatus, NetdiskConstants.FILE_STATUS_DELETED)
            .orderByDesc(NimbusFile::getId)
            .last("LIMIT 1"));
        if (existing != null) {
            key = existing.getStorageKey();
            redisUtils.set(cacheKey, key, HASH_TTL);
        }
        return key;
    }

    /** 创建文件记录并累加配额 */
    private NimbusFile createFileRecord(Long userId, Long folderId, String fileName, Long fileSize,
                                        String fileHash, String storageKey) {
        NimbusFile file = new NimbusFile();
        file.setUserId(userId);
        file.setFolderId(folderId);
        file.setFileName(fileName);
        file.setFileExt(FileServiceImpl.extractExt(fileName));
        file.setFileSize(fileSize);
        file.setFileHash(fileHash.toLowerCase(Locale.ROOT));
        file.setStorageKey(storageKey);
        file.setMimeType(mimeOf(fileName));
        file.setStatus(NetdiskConstants.FILE_STATUS_NORMAL);
        file.setIsStarred(NetdiskConstants.STAR_NO);
        file.setVersion(1);
        nimbusFileMapper.insert(file);
        quotaService.changeUsage(userId, fileSize);
        return file;
    }

    private UploadInitVO instantVo(NimbusFile file) {
        UploadInitVO vo = new UploadInitVO();
        vo.setInstant(true);
        vo.setFile(file);
        return vo;
    }

    private UploadTask getTask(String uploadId) {
        UploadTask task = redisUtils.get(TASK_KEY_PREFIX + uploadId);
        if (task == null) {
            throw new BusinessException(ErrorCode.UPLOAD_TASK_NOT_EXIST, "上传任务不存在或已过期: " + uploadId);
        }
        return task;
    }

    private void cleanTask(UploadTask task) {
        redisUtils.delete(TASK_KEY_PREFIX + task.getUploadId());
        redisUtils.deleteByPattern(CHUNK_KEY_PREFIX + task.getUploadId() + ":*");
    }

    /** 已上传分片序号(断点续传), 缺失的需客户端重传 */
    private List<Integer> collectExistChunks(String uploadId, int chunkCount) {
        List<Integer> existed = new ArrayList<>();
        for (String key : redisUtils.keys(CHUNK_KEY_PREFIX + uploadId + ":*")) {
            try {
                int index = Integer.parseInt(key.substring(key.lastIndexOf(':') + 1));
                if (index >= 0 && index < chunkCount) {
                    existed.add(index);
                }
            } catch (NumberFormatException ignored) {
            }
        }
        return existed;
    }

    /** 存储对象 key: 按用户与日期分层, 避免单目录热点 */
    private String buildObjectKey(Long userId, String fileName) {
        String ext = FileServiceImpl.extractExt(fileName);
        String suffix = ext == null ? "" : "." + ext;
        return "netdisk/" + userId + "/" + LocalDate.now().format(KEY_DATE_FORMATTER) + "/"
            + UUID.randomUUID() + suffix;
    }

    private String hashCacheKey(String fileHash) {
        return HASH_KEY_PREFIX + fileHash;
    }

    private String mimeOf(String fileName) {
        String ext = FileServiceImpl.extractExt(fileName);
        if (ext == null) {
            return "application/octet-stream";
        }
        return MIME_MAP.getOrDefault(ext, "application/octet-stream");
    }

    /** 文件名校验: 去除客户端可能带入的路径与非法字符 */
    private String cleanFileName(String original) {
        if (!StringUtils.hasText(original)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "文件名不能为空");
        }
        String name = original.replace('\\', '/');
        int slash = name.lastIndexOf('/');
        if (slash >= 0) {
            name = name.substring(slash + 1);
        }
        return StringUtils.hasText(name) ? name : "unnamed";
    }

    private static String sha256Hex(byte[] data) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(data));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 不可用", e);
        }
    }

    /**
     * 上传任务缓存模型: 初始化时写入 Redis, 合并时读取校验
     */
    @Data
    @NoArgsConstructor
    public static class UploadTask {

        /** 上传任务 id */
        private String uploadId;

        /** 所属用户 */
        private Long userId;

        /** 文件名 */
        private String fileName;

        /** 扩展名 */
        private String fileExt;

        /** MIME 类型 */
        private String mimeType;

        /** 文件大小 */
        private Long fileSize;

        /** 内容哈希 */
        private String fileHash;

        /** 目标文件夹 */
        private Long folderId;

        /** 存储对象 key */
        private String objectKey;

        /** 分片总数 */
        private Integer chunkCount;

        /** 分片大小 */
        private Long chunkSize;

        /** 版本上传目标文件 id, 为空表示新建文件 */
        private Long fileId;
    }
}