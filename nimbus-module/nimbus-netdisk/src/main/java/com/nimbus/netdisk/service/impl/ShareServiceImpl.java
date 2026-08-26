package com.nimbus.netdisk.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.nimbus.common.exception.BusinessException;
import com.nimbus.common.exception.ErrorCode;
import com.nimbus.common.model.PageQuery;
import com.nimbus.common.model.PageResult;
import com.nimbus.mybatis.utils.PageUtils;
import com.nimbus.netdisk.constant.NetdiskConstants;
import com.nimbus.netdisk.mapper.NimbusFileMapper;
import com.nimbus.netdisk.mapper.NimbusFolderMapper;
import com.nimbus.netdisk.mapper.NimbusShareItemMapper;
import com.nimbus.netdisk.mapper.NimbusShareMapper;
import com.nimbus.netdisk.model.dto.ShareAccessDTO;
import com.nimbus.netdisk.model.dto.ShareCreateDTO;
import com.nimbus.netdisk.model.dto.ShareSaveDTO;
import com.nimbus.netdisk.model.entity.NimbusFile;
import com.nimbus.netdisk.model.entity.NimbusFolder;
import com.nimbus.netdisk.model.entity.NimbusShare;
import com.nimbus.netdisk.model.entity.NimbusShareItem;
import com.nimbus.netdisk.model.vo.ShareAccessVO;
import com.nimbus.netdisk.model.vo.ShareItemVO;
import com.nimbus.netdisk.service.FileService;
import com.nimbus.netdisk.service.FolderService;
import com.nimbus.netdisk.service.ShareService;
import com.nimbus.redisson.constant.CacheNames;
import com.nimbus.redisson.utils.RedisUtils;
import com.nimbus.system.service.QuotaService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 文件分享业务实现
 * <ul>
 *     <li>短码: 分享主键 62 进制编码, 无碰撞</li>
 *     <li>访问: Redis 缓存 + 提取码/有效期/状态校验</li>
 *     <li>权限: 1预览 2下载 3转存, 逐级开放</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ShareServiceImpl implements ShareService {

    private static final String BASE62 = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";

    private static final Duration CACHE_TTL_FOREVER = Duration.ofDays(7);
    private static final Duration CACHE_TTL_EXPIRED_DAYS = Duration.ofHours(24);

    private final NimbusShareMapper nimbusShareMapper;

    private final NimbusShareItemMapper nimbusShareItemMapper;

    private final NimbusFileMapper nimbusFileMapper;

    private final NimbusFolderMapper nimbusFolderMapper;

    private final RedisUtils redisUtils;

    private final QuotaService quotaService;

    private final FileService fileService;

    private final FolderService folderService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public NimbusShare create(Long userId, ShareCreateDTO dto) {
        Integer targetType = dto.getTargetType();
        if (!Objects.equals(targetType, NetdiskConstants.TARGET_TYPE_FILE)
            && !Objects.equals(targetType, NetdiskConstants.TARGET_TYPE_FOLDER)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "分享目标类型不合法");
        }
        // 目标归属校验
        for (Long targetId : dto.getTargetIds()) {
            if (Objects.equals(targetType, NetdiskConstants.TARGET_TYPE_FILE)) {
                fileService.getOwnedFile(userId, targetId, NetdiskConstants.FILE_STATUS_NORMAL);
            } else {
                folderService.getOwnedFolder(userId, targetId, NetdiskConstants.FOLDER_STATUS_NORMAL);
            }
        }
        // 密码分享必须带提取码
        if (Objects.equals(dto.getShareType(), NetdiskConstants.SHARE_TYPE_PASSWORD)
            && (dto.getPassword() == null || dto.getPassword().isBlank())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "密码分享必须设置提取码");
        }
        if (dto.getExpireType() == null || (dto.getExpireType() != NetdiskConstants.SHARE_EXPIRE_FOREVER
            && dto.getExpireType() != NetdiskConstants.SHARE_EXPIRE_DAYS)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "有效期类型不合法");
        }
        LocalDateTime expireTime = null;
        if (Objects.equals(dto.getExpireType(), NetdiskConstants.SHARE_EXPIRE_DAYS)) {
            if (dto.getExpireDays() == null || dto.getExpireDays() <= 0) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "有效天数必须大于0");
            }
            expireTime = LocalDateTime.now().plusDays(dto.getExpireDays());
        }

        NimbusShare share = new NimbusShare();
        share.setUserId(userId);
        share.setShareType(dto.getShareType());
        share.setPassword(Objects.equals(dto.getShareType(), NetdiskConstants.SHARE_TYPE_PASSWORD)
            ? dto.getPassword() : null);
        share.setPermission(dto.getPermission() == null ? NetdiskConstants.SHARE_PERMISSION_ALL
            : dto.getPermission());
        share.setExpireType(dto.getExpireType());
        share.setExpireTime(expireTime);
        share.setViewCount(0);
        share.setSaveCount(0);
        share.setStatus(NetdiskConstants.SHARE_STATUS_VALID);
        nimbusShareMapper.insert(share);
        // 短码基于主键生成, 全局唯一
        share.setShortCode(encodeBase62(share.getId()));
        nimbusShareMapper.updateById(share);

        for (Long targetId : dto.getTargetIds()) {
            NimbusShareItem item = new NimbusShareItem();
            item.setShareId(share.getId());
            item.setTargetType(targetType);
            item.setTargetId(targetId);
            nimbusShareItemMapper.insert(item);
        }
        cacheShare(share);
        return share;
    }

    @Override
    public ShareAccessVO access(ShareAccessDTO dto) {
        NimbusShare share = getValidatedShare(dto.getCode(), dto.getPassword());
        increaseViewCount(share.getId());
        return new ShareAccessVO(share, listItems(dto.getCode(), dto.getPassword(), null));
    }

    @Override
    public List<ShareItemVO> listItems(String code, String password, Long folderId) {
        NimbusShare share = getValidatedShare(code, password);
        Map<Long, NimbusFolder> folderIndex = nimbusFolderMapper.selectList(new LambdaQueryWrapper<NimbusFolder>()
                .eq(NimbusFolder::getUserId, share.getUserId()))
            .stream().collect(Collectors.toMap(NimbusFolder::getId, Function.identity(), (a, b) -> a));
        if (folderId != null && folderId != NetdiskConstants.ROOT_FOLDER_ID) {
            return childrenOf(share, folderId, folderIndex);
        }
        // 首层: 文件目标直接返回, 文件夹目标返回其子级
        List<ShareItemVO> items = new ArrayList<>();
        for (NimbusShareItem item : shareItems(share.getId())) {
            if (Objects.equals(item.getTargetType(), NetdiskConstants.TARGET_TYPE_FILE)) {
                NimbusFile file = nimbusFileMapper.selectById(item.getTargetId());
                if (file != null && Objects.equals(file.getStatus(), NetdiskConstants.FILE_STATUS_NORMAL)) {
                    items.add(toItem(file));
                }
            } else {
                items.addAll(childrenOf(share, item.getTargetId(), folderIndex));
            }
        }
        return items;
    }

    @Override
    public NimbusFile getShareDownloadFile(String code, String password, Long fileId) {
        NimbusShare share = getValidatedShare(code, password);
        if (share.getPermission() == null
            || (share.getPermission() & NetdiskConstants.SHARE_PERMISSION_DOWNLOAD) == 0) {
            throw new BusinessException(ErrorCode.SHARE_OPERATION_FORBIDDEN, "该分享未开放下载");
        }
        NimbusFile file = nimbusFileMapper.selectById(fileId);
        if (file == null || !Objects.equals(file.getStatus(), NetdiskConstants.FILE_STATUS_NORMAL)) {
            throw new BusinessException(ErrorCode.SHARE_NOT_EXIST, "分享目标不存在或已删除");
        }
        if (!belongsToShare(share, file)) {
            throw new BusinessException(ErrorCode.SHARE_NOT_EXIST, "文件不属于该分享");
        }
        return file;
    }

    @Override
    public PageResult<NimbusShare> myShares(Long userId, PageQuery query) {
        Page<NimbusShare> page = nimbusShareMapper.selectPage(PageUtils.toPage(query),
            new LambdaQueryWrapper<NimbusShare>()
                .eq(NimbusShare::getUserId, userId)
                .orderByDesc(NimbusShare::getCreateTime));
        return PageUtils.toResult(page);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void cancel(Long userId, Long shareId) {
        NimbusShare share = getOwnedShare(userId, shareId);
        NimbusShare update = new NimbusShare();
        update.setId(share.getId());
        update.setStatus(NetdiskConstants.SHARE_STATUS_CANCELED);
        nimbusShareMapper.updateById(update);
        redisUtils.delete(cacheKey(share.getShortCode()));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public long save(Long userId, ShareSaveDTO dto) {
        NimbusShare share = getValidatedShare(dto.getCode(), dto.getPassword());
        if (share.getPermission() == null
            || (share.getPermission() & NetdiskConstants.SHARE_PERMISSION_SAVE) == 0) {
            throw new BusinessException(ErrorCode.SHARE_OPERATION_FORBIDDEN, "该分享未开放转存");
        }
        Long folderId = dto.getFolderId() == null ? NetdiskConstants.ROOT_FOLDER_ID : dto.getFolderId();
        if (folderId != NetdiskConstants.ROOT_FOLDER_ID) {
            folderService.getOwnedFolder(userId, folderId, NetdiskConstants.FOLDER_STATUS_NORMAL);
        }
        List<NimbusShareItem> items = shareItems(share.getId());
        long totalSize = estimateSaveSize(share, items);
        quotaService.checkQuota(userId, totalSize);

        long count = 0;
        for (NimbusShareItem item : items) {
            if (Objects.equals(item.getTargetType(), NetdiskConstants.TARGET_TYPE_FILE)) {
                NimbusFile file = nimbusFileMapper.selectOne(new LambdaQueryWrapper<NimbusFile>()
                    .eq(NimbusFile::getId, item.getTargetId())
                    .eq(NimbusFile::getStatus, NetdiskConstants.FILE_STATUS_NORMAL));
                if (file != null) {
                    copyFileInto(userId, folderId, file);
                    count++;
                }
            } else {
                count += copyFolderInto(userId, folderId, item.getTargetId(), share.getUserId());
            }
        }
        // 转存计数
        nimbusShareMapper.update(null, new LambdaUpdateWrapper<NimbusShare>()
            .eq(NimbusShare::getId, share.getId())
            .setSql("save_count = save_count + 1"));
        return count;
    }

    /** 分享校验: 状态/有效期/提取码 */
    private NimbusShare getValidatedShare(String code, String password) {
        NimbusShare share = loadShare(code);
        if (share == null || !Objects.equals(share.getStatus(), NetdiskConstants.SHARE_STATUS_VALID)) {
            throw new BusinessException(ErrorCode.SHARE_NOT_EXIST, "分享不存在或已取消: " + code);
        }
        if (share.getExpireTime() != null && share.getExpireTime().isBefore(LocalDateTime.now())) {
            throw new BusinessException(ErrorCode.SHARE_EXPIRED, "分享已过期: " + code);
        }
        if (Objects.equals(share.getShareType(), NetdiskConstants.SHARE_TYPE_PASSWORD)
            && !Objects.equals(share.getPassword(), password)) {
            throw new BusinessException(ErrorCode.SHARE_PASSWORD_ERROR, "提取码错误");
        }
        return share;
    }

    private NimbusShare loadShare(String shortCode) {
        String key = cacheKey(shortCode);
        NimbusShare share = redisUtils.get(key);
        if (share != null) {
            return share;
        }
        share = nimbusShareMapper.selectOne(new LambdaQueryWrapper<NimbusShare>()
            .eq(NimbusShare::getShortCode, shortCode));
        if (share != null) {
            cacheShare(share);
        }
        return share;
    }

    private void cacheShare(NimbusShare share) {
        Duration ttl = Objects.equals(share.getExpireType(), NetdiskConstants.SHARE_EXPIRE_DAYS)
            && share.getExpireTime() != null
            ? Duration.between(LocalDateTime.now(), share.getExpireTime())
            : CACHE_TTL_FOREVER;
        if (ttl.isNegative() || ttl.isZero()) {
            ttl = CACHE_TTL_EXPIRED_DAYS;
        }
        redisUtils.set(cacheKey(share.getShortCode()), share, ttl);
    }

    private void increaseViewCount(Long shareId) {
        nimbusShareMapper.update(null, new LambdaUpdateWrapper<NimbusShare>()
            .eq(NimbusShare::getId, shareId)
            .setSql("view_count = view_count + 1"));
    }

    private List<NimbusShareItem> shareItems(Long shareId) {
        return nimbusShareItemMapper.selectList(new LambdaQueryWrapper<NimbusShareItem>()
            .eq(NimbusShareItem::getShareId, shareId));
    }

    /** 分享目标子级列表(文件夹目标) */
    private List<ShareItemVO> childrenOf(NimbusShare share, Long folderId, Map<Long, NimbusFolder> folderIndex) {
        NimbusFolder folder = folderIndex.get(folderId);
        if (folder == null || !Objects.equals(folder.getUserId(), share.getUserId())) {
            throw new BusinessException(ErrorCode.SHARE_NOT_EXIST, "分享目标不存在: " + folderId);
        }
        List<ShareItemVO> items = new ArrayList<>();
        nimbusFolderMapper.selectList(new LambdaQueryWrapper<NimbusFolder>()
                .eq(NimbusFolder::getUserId, share.getUserId())
                .eq(NimbusFolder::getParentId, folderId)
                .eq(NimbusFolder::getStatus, NetdiskConstants.FOLDER_STATUS_NORMAL)
                .orderByAsc(NimbusFolder::getFolderName))
            .forEach(f -> items.add(toItem(f)));
        nimbusFileMapper.selectList(new LambdaQueryWrapper<NimbusFile>()
                .eq(NimbusFile::getUserId, share.getUserId())
                .eq(NimbusFile::getFolderId, folderId)
                .eq(NimbusFile::getStatus, NetdiskConstants.FILE_STATUS_NORMAL)
                .orderByDesc(NimbusFile::getUpdateTime))
            .forEach(file -> items.add(toItem(file)));
        return items;
    }

    /** 文件是否属于分享目标(文件目标直接命中和, 文件夹目标按路径前缀) */
    private boolean belongsToShare(NimbusShare share, NimbusFile file) {
        for (NimbusShareItem item : shareItems(share.getId())) {
            if (Objects.equals(item.getTargetType(), NetdiskConstants.TARGET_TYPE_FILE)) {
                if (Objects.equals(item.getTargetId(), file.getId())) {
                    return true;
                }
            } else {
                String targetPath = folderPathOf(share.getUserId(), item.getTargetId());
                String fileFolderPath = file.getFolderId() == null || file.getFolderId() == NetdiskConstants.ROOT_FOLDER_ID
                    ? "/" : folderPathOf(share.getUserId(), file.getFolderId());
                if (fileFolderPath.startsWith(targetPath)) {
                    return true;
                }
            }
        }
        return false;
    }

    private String folderPathOf(Long userId, Long folderId) {
        NimbusFolder folder = nimbusFolderMapper.selectOne(new LambdaQueryWrapper<NimbusFolder>()
            .eq(NimbusFolder::getId, folderId)
            .eq(NimbusFolder::getUserId, userId));
        return folder == null ? "/" : folder.getFolderPath();
    }

    /** 估算转存占用空间 */
    private long estimateSaveSize(NimbusShare share, List<NimbusShareItem> items) {
        long total = 0;
        for (NimbusShareItem item : items) {
            if (Objects.equals(item.getTargetType(), NetdiskConstants.TARGET_TYPE_FILE)) {
                NimbusFile file = nimbusFileMapper.selectById(item.getTargetId());
                if (file != null) {
                    total += file.getFileSize();
                }
            } else {
                List<NimbusFile> files = nimbusFileMapper.selectList(new LambdaQueryWrapper<NimbusFile>()
                    .eq(NimbusFile::getUserId, share.getUserId())
                    .eq(NimbusFile::getStatus, NetdiskConstants.FILE_STATUS_NORMAL)
                    .in(NimbusFile::getFolderId, folderService.listSubtreeIds(share.getUserId(), item.getTargetId())));
                for (NimbusFile file : files) {
                    total += file.getFileSize();
                }
            }
        }
        return total;
    }

    /** 单文件转存: 共享同一存储对象, 重名自动追加序号 */
    private void copyFileInto(Long userId, Long folderId, NimbusFile source) {
        String fileName = uniqueName(userId, folderId, source.getFileName());
        NimbusFile copy = new NimbusFile();
        copy.setUserId(userId);
        copy.setFolderId(folderId);
        copy.setFileName(fileName);
        copy.setFileExt(source.getFileExt());
        copy.setFileSize(source.getFileSize());
        copy.setFileHash(source.getFileHash());
        copy.setStorageKey(source.getStorageKey());
        copy.setMimeType(source.getMimeType());
        copy.setStatus(NetdiskConstants.FILE_STATUS_NORMAL);
        copy.setIsStarred(NetdiskConstants.STAR_NO);
        copy.setVersion(1);
        nimbusFileMapper.insert(copy);
        quotaService.changeUsage(userId, source.getFileSize());
    }

    /** 文件夹(含子树)整体转存, 返回转存文件数 */
    private long copyFolderInto(Long userId, Long targetFolderId, Long sourceFolderId, Long sourceUserId) {
        NimbusFolder root = folderService.getOwnedFolder(sourceUserId, sourceFolderId, NetdiskConstants.FOLDER_STATUS_NORMAL);
        List<NimbusFolder> subtree = nimbusFolderMapper.selectList(new LambdaQueryWrapper<NimbusFolder>()
            .eq(NimbusFolder::getUserId, sourceUserId)
            .and(w -> w.eq(NimbusFolder::getId, sourceFolderId)
                .or().likeRight(NimbusFolder::getFolderPath, root.getFolderPath())));
        // 父先子后, 保证映射可用
        subtree.sort(Comparator.comparing(NimbusFolder::getDepth));
        Map<Long, Long> idMapping = new HashMap<>();
        Map<Long, String> pathMapping = new HashMap<>();
        Map<Long, Integer> depthMapping = new HashMap<>();
        for (NimbusFolder folder : subtree) {
            Long newParentId = Objects.equals(folder.getId(), sourceFolderId)
                ? targetFolderId : idMapping.get(folder.getParentId());
            String parentPath = (newParentId == null || newParentId == NetdiskConstants.ROOT_FOLDER_ID)
                ? "/" : pathMapping.get(newParentId);
            int parentDepth = (newParentId == null || newParentId == NetdiskConstants.ROOT_FOLDER_ID)
                ? 0 : depthMapping.get(newParentId);
            String folderName = uniqueFolderName(userId, newParentId, folder.getFolderName());
            NimbusFolder clone = new NimbusFolder();
            clone.setUserId(userId);
            clone.setParentId(newParentId);
            clone.setFolderName(folderName);
            clone.setStatus(NetdiskConstants.FOLDER_STATUS_NORMAL);
            nimbusFolderMapper.insert(clone);
            clone.setFolderPath(parentPath + clone.getId() + "/");
            clone.setDepth(parentDepth + 1);
            nimbusFolderMapper.updateById(clone);
            idMapping.put(folder.getId(), clone.getId());
            pathMapping.put(clone.getId(), clone.getFolderPath());
            depthMapping.put(clone.getId(), clone.getDepth());
        }
        // 拷贝各文件到映射后的文件夹
        long count = 0;
        for (NimbusFolder folder : subtree) {
            List<NimbusFile> files = nimbusFileMapper.selectList(new LambdaQueryWrapper<NimbusFile>()
                .eq(NimbusFile::getUserId, sourceUserId)
                .eq(NimbusFile::getFolderId, folder.getId())
                .eq(NimbusFile::getStatus, NetdiskConstants.FILE_STATUS_NORMAL));
            for (NimbusFile file : files) {
                copyFileInto(userId, idMapping.get(folder.getId()), file);
                count++;
            }
        }
        return count;
    }

    /** 文件夹重名去重 */
    private String uniqueFolderName(Long userId, Long parentId, String name) {
        String candidate = name;
        int index = 1;
        while (nimbusFolderMapper.selectCount(new LambdaQueryWrapper<NimbusFolder>()
            .eq(NimbusFolder::getUserId, userId)
            .eq(NimbusFolder::getParentId, parentId)
            .eq(NimbusFolder::getStatus, NetdiskConstants.FOLDER_STATUS_NORMAL)
            .eq(NimbusFolder::getFolderName, candidate)) > 0) {
            candidate = appendIndex(name, index++);
        }
        return candidate;
    }

    /** 文件重名去重(文件夹与文件共用命名空间) */
    private String uniqueName(Long userId, Long folderId, String name) {
        String candidate = name;
        int index = 1;
        while (nameExists(userId, folderId, candidate)) {
            candidate = appendIndex(name, index++);
        }
        return candidate;
    }

    private boolean nameExists(Long userId, Long folderId, String name) {
        Long fileCount = nimbusFileMapper.selectCount(new LambdaQueryWrapper<NimbusFile>()
            .eq(NimbusFile::getUserId, userId)
            .eq(NimbusFile::getFolderId, folderId)
            .eq(NimbusFile::getStatus, NetdiskConstants.FILE_STATUS_NORMAL)
            .eq(NimbusFile::getFileName, name));
        if (fileCount > 0) {
            return true;
        }
        return nimbusFolderMapper.selectCount(new LambdaQueryWrapper<NimbusFolder>()
            .eq(NimbusFolder::getUserId, userId)
            .eq(NimbusFolder::getParentId, folderId)
            .eq(NimbusFolder::getStatus, NetdiskConstants.FOLDER_STATUS_NORMAL)
            .eq(NimbusFolder::getFolderName, name)) > 0;
    }

    /** name.ext -> name (1).ext */
    private String appendIndex(String name, int index) {
        int dot = name.lastIndexOf('.');
        if (dot <= 0) {
            return name + " (" + index + ")";
        }
        return name.substring(0, dot) + " (" + index + ")" + name.substring(dot);
    }

    private NimbusShare getOwnedShare(Long userId, Long shareId) {
        NimbusShare share = nimbusShareMapper.selectOne(new LambdaQueryWrapper<NimbusShare>()
            .eq(NimbusShare::getId, shareId)
            .eq(NimbusShare::getUserId, userId));
        if (share == null) {
            throw new BusinessException(ErrorCode.SHARE_NOT_EXIST, "分享不存在: " + shareId);
        }
        return share;
    }

    private ShareItemVO toItem(NimbusFolder folder) {
        return new ShareItemVO(NetdiskConstants.TARGET_TYPE_FOLDER, folder.getId(), folder.getFolderName(),
            null, null, null);
    }

    private ShareItemVO toItem(NimbusFile file) {
        return new ShareItemVO(NetdiskConstants.TARGET_TYPE_FILE, file.getId(), file.getFileName(),
            file.getFileExt(), file.getFileSize(), null);
    }

    private String encodeBase62(long num) {
        StringBuilder sb = new StringBuilder();
        while (num > 0) {
            sb.append(BASE62.charAt((int) (num % 62)));
            num /= 62;
        }
        return sb.reverse().toString();
    }

    private String cacheKey(String shortCode) {
        return CacheNames.NETDISK_SHARE + ":" + shortCode;
    }
}