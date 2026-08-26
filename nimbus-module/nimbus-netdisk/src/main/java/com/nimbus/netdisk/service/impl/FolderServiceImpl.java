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
import com.nimbus.netdisk.model.dto.FolderCreateDTO;
import com.nimbus.netdisk.model.dto.FolderMoveDTO;
import com.nimbus.netdisk.model.dto.FolderRenameDTO;
import com.nimbus.netdisk.model.entity.NimbusFile;
import com.nimbus.netdisk.model.entity.NimbusFolder;
import com.nimbus.netdisk.model.vo.BreadcrumbVO;
import com.nimbus.netdisk.model.vo.FolderContentVO;
import com.nimbus.netdisk.model.vo.FolderTreeVO;
import com.nimbus.netdisk.service.FolderService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 文件夹业务实现, 物化路径方案支撑子树查询/移动/面包屑
 */
@Service
@RequiredArgsConstructor
public class FolderServiceImpl implements FolderService {

    private final NimbusFolderMapper nimbusFolderMapper;

    private final NimbusFileMapper nimbusFileMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createFolder(Long userId, FolderCreateDTO dto) {
        Long parentId = dto.getParentId() == null ? NetdiskConstants.ROOT_FOLDER_ID : dto.getParentId();
        NimbusFolder parent = parentId == NetdiskConstants.ROOT_FOLDER_ID
            ? null : getOwnedFolder(userId, parentId, NetdiskConstants.FOLDER_STATUS_NORMAL);
        checkNameAvailable(userId, parentId, dto.getFolderName(), null, null);

        NimbusFolder folder = new NimbusFolder();
        folder.setUserId(userId);
        folder.setParentId(parentId);
        folder.setFolderName(dto.getFolderName());
        folder.setStatus(NetdiskConstants.FOLDER_STATUS_NORMAL);
        nimbusFolderMapper.insert(folder);

        // 物化路径: 父路径 + 自身 id + "/"
        folder.setFolderPath((parent == null ? "/" : parent.getFolderPath()) + folder.getId() + "/");
        folder.setDepth(parent == null ? 1 : parent.getDepth() + 1);
        nimbusFolderMapper.updateById(folder);
        return folder.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void renameFolder(Long userId, FolderRenameDTO dto) {
        NimbusFolder folder = getOwnedFolder(userId, dto.getId(), NetdiskConstants.FOLDER_STATUS_NORMAL);
        checkNameAvailable(userId, folder.getParentId(), dto.getFolderName(), folder.getId(), null);
        NimbusFolder update = new NimbusFolder();
        update.setId(folder.getId());
        update.setFolderName(dto.getFolderName());
        nimbusFolderMapper.updateById(update);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void moveFolder(Long userId, FolderMoveDTO dto) {
        NimbusFolder folder = getOwnedFolder(userId, dto.getId(), NetdiskConstants.FOLDER_STATUS_NORMAL);
        Long targetId = dto.getTargetParentId() == null ? NetdiskConstants.ROOT_FOLDER_ID : dto.getTargetParentId();
        NimbusFolder target = targetId == NetdiskConstants.ROOT_FOLDER_ID
            ? null : getOwnedFolder(userId, targetId, NetdiskConstants.FOLDER_STATUS_NORMAL);

        // 循环引用检测: 目标位于被移动文件夹的子树内
        if (target != null && (target.getId().equals(folder.getId())
            || target.getFolderPath().startsWith(folder.getFolderPath()))) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "不能将文件夹移动到其自身或其子目录下");
        }
        checkNameAvailable(userId, targetId, folder.getFolderName(), folder.getId(), null);

        NimbusFolder update = new NimbusFolder();
        update.setId(folder.getId());
        update.setParentId(targetId);
        nimbusFolderMapper.updateById(update);
        recomputeSubtreePaths(userId, folder, target);
    }

    @Override
    public List<FolderTreeVO> getTree(Long userId) {
        List<NimbusFolder> folders = nimbusFolderMapper.selectList(new LambdaQueryWrapper<NimbusFolder>()
            .eq(NimbusFolder::getUserId, userId)
            .eq(NimbusFolder::getStatus, NetdiskConstants.FOLDER_STATUS_NORMAL)
            .orderByAsc(NimbusFolder::getFolderName));
        Map<Long, FolderTreeVO> nodes = folders.stream().collect(LinkedHashMap::new,
            (map, folder) -> map.put(folder.getId(), toTreeVO(folder)), Map::putAll);
        // 按深度分层挂载, 避免父节点尚未创建时丢失
        List<FolderTreeVO> roots = new ArrayList<>();
        Map<Long, List<FolderTreeVO>> childrenMap = folders.stream().collect(Collectors.groupingBy(
            NimbusFolder::getParentId,
            Collectors.mapping(this::toTreeVO, Collectors.toList())));
        for (FolderTreeVO node : nodes.values()) {
            node.setChildren(childrenMap.getOrDefault(node.getId(), List.of()));
            if (node.getParentId() == NetdiskConstants.ROOT_FOLDER_ID
                || !nodes.containsKey(node.getParentId())) {
                roots.add(node);
            }
        }
        return roots;
    }

    /** 文件夹转树节点 */
    private FolderTreeVO toTreeVO(NimbusFolder folder) {
        FolderTreeVO node = new FolderTreeVO();
        node.setId(folder.getId());
        node.setParentId(folder.getParentId());
        node.setFolderName(folder.getFolderName());
        return node;
    }

    @Override
    public List<BreadcrumbVO> getBreadcrumb(Long userId, Long folderId) {
        if (folderId == null || folderId == NetdiskConstants.ROOT_FOLDER_ID) {
            return List.of();
        }
        NimbusFolder folder = getOwnedFolder(userId, folderId, NetdiskConstants.FOLDER_STATUS_NORMAL);
        // 解析物化路径 /1/5/ 为逐级 id
        List<Long> ids = new ArrayList<>();
        for (String segment : folder.getFolderPath().split("/")) {
            if (!segment.isBlank()) {
                ids.add(Long.parseLong(segment));
            }
        }
        if (ids.isEmpty()) {
            return List.of();
        }
        Map<Long, NimbusFolder> map = nimbusFolderMapper.selectList(new LambdaQueryWrapper<NimbusFolder>()
                .eq(NimbusFolder::getUserId, userId)
                .in(NimbusFolder::getId, ids))
            .stream().collect(Collectors.toMap(NimbusFolder::getId, f -> f));
        List<BreadcrumbVO> breadcrumb = new ArrayList<>();
        breadcrumb.add(new BreadcrumbVO(NetdiskConstants.ROOT_FOLDER_ID, "我的文件"));
        ids.forEach(id -> {
            NimbusFolder node = map.get(id);
            if (node != null) {
                breadcrumb.add(new BreadcrumbVO(node.getId(), node.getFolderName()));
            }
        });
        return breadcrumb;
    }

    /** 便捷重载(供内部调用, 默认时间倒序) */
    public FolderContentVO getContent(Long userId, Long folderId, PageQuery query) {
        return getContent(userId, folderId, query, null, null);
    }

    @Override
    public FolderContentVO getContent(Long userId, Long folderId, PageQuery query, String sortKey, String order) {
        Long id = folderId == null ? NetdiskConstants.ROOT_FOLDER_ID : folderId;
        if (id != NetdiskConstants.ROOT_FOLDER_ID) {
            getOwnedFolder(userId, id, NetdiskConstants.FOLDER_STATUS_NORMAL);
        }
        // 文件夹先展示, 按名称排序
        List<NimbusFolder> folders = nimbusFolderMapper.selectList(new LambdaQueryWrapper<NimbusFolder>()
            .eq(NimbusFolder::getUserId, userId)
            .eq(NimbusFolder::getParentId, id)
            .eq(NimbusFolder::getStatus, NetdiskConstants.FOLDER_STATUS_NORMAL)
            .orderByAsc(NimbusFolder::getFolderName));
        LambdaQueryWrapper<NimbusFile> wrapper = new LambdaQueryWrapper<NimbusFile>()
            .eq(NimbusFile::getUserId, userId)
            .eq(NimbusFile::getFolderId, id)
            .eq(NimbusFile::getStatus, NetdiskConstants.FILE_STATUS_NORMAL);
        appendOrder(wrapper, sortKey, order);
        Page<NimbusFile> page = nimbusFileMapper.selectPage(PageUtils.toPage(query), wrapper);
        return new FolderContentVO(folders, PageUtils.toResult(page));
    }

    /** 文件排序: name/time/size + asc/desc, 默认修改时间倒序 */
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

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteToRecycle(Long userId, Long folderId) {
        NimbusFolder folder = getOwnedFolder(userId, folderId, NetdiskConstants.FOLDER_STATUS_NORMAL);
        List<Long> folderIds = listSubtreeIds(userId, folderId);
        LocalDateTime now = LocalDateTime.now();
        // 子树文件夹 + 子文件全部进回收站
        nimbusFolderMapper.update(null, new LambdaUpdateWrapper<NimbusFolder>()
            .in(NimbusFolder::getId, folderIds)
            .set(NimbusFolder::getStatus, NetdiskConstants.FOLDER_STATUS_RECYCLED)
            .set(NimbusFolder::getDeleteTime, now));
        nimbusFileMapper.update(null, new LambdaUpdateWrapper<NimbusFile>()
            .eq(NimbusFile::getUserId, userId)
            .in(NimbusFile::getFolderId, folderIds)
            .set(NimbusFile::getStatus, NetdiskConstants.FILE_STATUS_RECYCLED)
            .set(NimbusFile::getDeleteTime, now));
    }

    @Override
    public NimbusFolder getOwnedFolder(Long userId, Long folderId) {
        return getOwnedFolder(userId, folderId, null);
    }

    @Override
    public NimbusFolder getOwnedFolder(Long userId, Long folderId, Integer status) {
        LambdaQueryWrapper<NimbusFolder> wrapper = new LambdaQueryWrapper<NimbusFolder>()
            .eq(NimbusFolder::getId, folderId)
            .eq(NimbusFolder::getUserId, userId)
            .eq(status != null, NimbusFolder::getStatus, status);
        NimbusFolder folder = nimbusFolderMapper.selectOne(wrapper);
        if (folder == null) {
            throw new BusinessException(ErrorCode.FOLDER_NOT_EXIST, "文件夹不存在或已删除: " + folderId);
        }
        return folder;
    }

    @Override
    public List<Long> listSubtreeIds(Long userId, Long folderId) {
        NimbusFolder folder = getOwnedFolder(userId, folderId, null);
        return nimbusFolderMapper.selectList(new LambdaQueryWrapper<NimbusFolder>()
                .eq(NimbusFolder::getUserId, userId)
                .and(w -> w.eq(NimbusFolder::getId, folderId)
                    .or().likeRight(NimbusFolder::getFolderPath, folder.getFolderPath())))
            .stream().map(NimbusFolder::getId).toList();
    }

    @Override
    public void checkNameAvailable(Long userId, Long parentId, String name, Long excludeFolderId, Long excludeFileId) {
        Long folderCount = nimbusFolderMapper.selectCount(new LambdaQueryWrapper<NimbusFolder>()
            .eq(NimbusFolder::getUserId, userId)
            .eq(NimbusFolder::getParentId, parentId)
            .eq(NimbusFolder::getStatus, NetdiskConstants.FOLDER_STATUS_NORMAL)
            .eq(NimbusFolder::getFolderName, name)
            .ne(excludeFolderId != null, NimbusFolder::getId, excludeFolderId));
        if (folderCount > 0) {
            throw new BusinessException(ErrorCode.NAME_CONFLICT, "同名文件夹已存在: " + name);
        }
        Long fileCount = nimbusFileMapper.selectCount(new LambdaQueryWrapper<NimbusFile>()
            .eq(NimbusFile::getUserId, userId)
            .eq(NimbusFile::getFolderId, parentId)
            .eq(NimbusFile::getStatus, NetdiskConstants.FILE_STATUS_NORMAL)
            .eq(NimbusFile::getFileName, name)
            .ne(excludeFileId != null, NimbusFile::getId, excludeFileId));
        if (fileCount > 0) {
            throw new BusinessException(ErrorCode.NAME_CONFLICT, "同名文件已存在: " + name);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void recomputeSubtreePaths(Long userId, NimbusFolder root, NimbusFolder parent) {
        // 一次性加载全部文件夹(含回收站), 按层级重算路径与深度
        List<NimbusFolder> all = nimbusFolderMapper.selectList(new LambdaQueryWrapper<NimbusFolder>()
            .eq(NimbusFolder::getUserId, userId));
        Map<Long, List<NimbusFolder>> byParent = all.stream()
            .collect(Collectors.groupingBy(NimbusFolder::getParentId));

        // parent 为 null 表示挂到根目录; 否则以 parent 的现路径为基准
        String rootPath = (parent == null ? "/" : parent.getFolderPath()) + root.getId() + "/";
        int rootDepth = (parent == null ? 0 : parent.getDepth()) + 1;

        Map<Long, String> newPaths = new HashMap<>();
        Map<Long, Integer> newDepths = new HashMap<>();
        List<NimbusFolder> queue = new ArrayList<>();
        queue.add(root);
        newPaths.put(root.getId(), rootPath);
        newDepths.put(root.getId(), rootDepth);
        while (!queue.isEmpty()) {
            NimbusFolder node = queue.remove(0);
            String nodePath = newPaths.get(node.getId());
            int nodeDepth = newDepths.get(node.getId());
            NimbusFolder update = new NimbusFolder();
            update.setId(node.getId());
            update.setFolderPath(nodePath);
            update.setDepth(nodeDepth);
            nimbusFolderMapper.updateById(update);
            // 子节点以新路径为基准继续重算
            for (NimbusFolder child : byParent.getOrDefault(node.getId(), List.of())) {
                newPaths.put(child.getId(), nodePath + child.getId() + "/");
                newDepths.put(child.getId(), nodeDepth + 1);
                queue.add(child);
            }
        }
    }
}