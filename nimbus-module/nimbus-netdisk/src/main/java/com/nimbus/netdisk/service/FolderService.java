package com.nimbus.netdisk.service;

import com.nimbus.common.model.PageQuery;
import com.nimbus.netdisk.model.dto.FolderCreateDTO;
import com.nimbus.netdisk.model.dto.FolderMoveDTO;
import com.nimbus.netdisk.model.dto.FolderRenameDTO;
import com.nimbus.netdisk.model.entity.NimbusFolder;
import com.nimbus.netdisk.model.vo.BreadcrumbVO;
import com.nimbus.netdisk.model.vo.FolderContentVO;
import com.nimbus.netdisk.model.vo.FolderTreeVO;

import java.util.List;

/**
 * 文件夹业务接口
 */
public interface FolderService {

    /** 新建文件夹, 返回文件夹 id */
    Long createFolder(Long userId, FolderCreateDTO dto);

    /** 重命名文件夹 */
    void renameFolder(Long userId, FolderRenameDTO dto);

    /** 移动文件夹(含子树路径重算), 禁止移动到自身或子目录 */
    void moveFolder(Long userId, FolderMoveDTO dto);

    /** 目录树 */
    List<FolderTreeVO> getTree(Long userId);

    /** 面包屑, 根目录返回空列表 */
    List<BreadcrumbVO> getBreadcrumb(Long userId, Long folderId);

    /** 文件夹内容: 子文件夹 + 文件分页, sortKey 支持 name/time/size, order 支持 asc/desc */
    FolderContentVO getContent(Long userId, Long folderId, PageQuery query, String sortKey, String order);

    /** 删除到回收站(含全部子树), 不释放配额 */
    void deleteToRecycle(Long userId, Long folderId);

    /** 获取用户自己正常状态的文件夹, 不存在抛业务异常 */
    NimbusFolder getOwnedFolder(Long userId, Long folderId);

    /** 获取用户自己的文件夹(按状态过滤, status 为 null 不过滤) */
    NimbusFolder getOwnedFolder(Long userId, Long folderId, Integer status);

    /** 文件夹子树 id 列表(含自身), 基于物化路径前缀 */
    List<Long> listSubtreeIds(Long userId, Long folderId);

    /** 同名检查(文件夹与文件共用命名空间) */
    void checkNameAvailable(Long userId, Long parentId, String name, Long excludeFolderId, Long excludeFileId);

    /** 重算子树物化路径与深度(移动/恢复后调用) */
    void recomputeSubtreePaths(Long userId, NimbusFolder root, NimbusFolder parent);
}