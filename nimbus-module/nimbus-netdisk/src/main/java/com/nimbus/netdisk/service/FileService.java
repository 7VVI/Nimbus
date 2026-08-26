package com.nimbus.netdisk.service;

import com.nimbus.common.model.PageResult;
import com.nimbus.netdisk.model.dto.FileCopyDTO;
import com.nimbus.netdisk.model.dto.FileMoveDTO;
import com.nimbus.netdisk.model.dto.FileQueryDTO;
import com.nimbus.netdisk.model.dto.FileRenameDTO;
import com.nimbus.netdisk.model.entity.NimbusFile;

import java.util.List;

/**
 * 文件业务接口
 */
public interface FileService {

    /** 文件列表/搜索(关键字/分类/收藏/排序), 仅正常状态 */
    PageResult<NimbusFile> page(Long userId, FileQueryDTO query);

    /** 最近文件, 按修改时间倒序 */
    List<NimbusFile> recent(Long userId, int limit);

    /** 收藏文件列表 */
    List<NimbusFile> starred(Long userId);

    /** 文件详情 */
    NimbusFile detail(Long userId, Long fileId);

    /** 重命名(可修改扩展名) */
    void rename(Long userId, Long fileId, FileRenameDTO dto);

    /** 移动文件 */
    void move(Long userId, Long fileId, FileMoveDTO dto);

    /** 复制文件(共享同一存储对象), 返回新记录 */
    NimbusFile copy(Long userId, Long fileId, FileCopyDTO dto);

    /** 设置收藏 */
    void star(Long userId, Long fileId, boolean starred);

    /** 软删除到回收站 */
    void deleteToRecycle(Long userId, Long fileId);

    /** 获取用户自己的文件(按状态过滤, status 为 null 不过滤) */
    NimbusFile getOwnedFile(Long userId, Long fileId, Integer status);

    /** 文件扩展名分类 */
    String categoryOf(String fileExt);

    /** 分类对应的扩展名集合, 用于列表过滤 */
    List<String> extensionsOf(String category);
}