package com.nimbus.netdisk.service;

import com.nimbus.common.model.PageQuery;
import com.nimbus.common.model.PageResult;
import com.nimbus.netdisk.model.dto.ShareAccessDTO;
import com.nimbus.netdisk.model.dto.ShareCreateDTO;
import com.nimbus.netdisk.model.dto.ShareSaveDTO;
import com.nimbus.netdisk.model.entity.NimbusFile;
import com.nimbus.netdisk.model.entity.NimbusShare;
import com.nimbus.netdisk.model.vo.ShareAccessVO;
import com.nimbus.netdisk.model.vo.ShareItemVO;

import java.util.List;

/**
 * 文件分享业务接口: 短链 + 提取码 + 有效期 + 权限控制 + 转存
 */
public interface ShareService {

    /** 创建分享, 返回分享记录 */
    NimbusShare create(Long userId, ShareCreateDTO dto);

    /** 访问分享(校验提取码/有效期), 返回首层目标 */
    ShareAccessVO access(ShareAccessDTO dto);

    /** 分享内目录浏览, folderId 为空返回首层 */
    List<ShareItemVO> listItems(String code, String password, Long folderId);

    /** 分享内下载文件(校验归属与下载权限) */
    NimbusFile getShareDownloadFile(String code, String password, Long fileId);

    /** 我的分享分页 */
    PageResult<NimbusShare> myShares(Long userId, PageQuery query);

    /** 取消分享 */
    void cancel(Long userId, Long shareId);

    /** 转存到我的网盘, 返回转存文件数 */
    long save(Long userId, ShareSaveDTO dto);
}