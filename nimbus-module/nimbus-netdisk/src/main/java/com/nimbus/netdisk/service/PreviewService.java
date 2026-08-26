package com.nimbus.netdisk.service;

import com.nimbus.netdisk.model.vo.PreviewVO;

/**
 * 文件预览业务接口
 */
public interface PreviewService {

    /** 预览信息: 分类 + 访问地址(直连或服务端流式) */
    PreviewVO getPreview(Long userId, Long fileId);
}