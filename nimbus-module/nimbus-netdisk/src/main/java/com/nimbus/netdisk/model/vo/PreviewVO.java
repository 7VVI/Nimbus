package com.nimbus.netdisk.model.vo;

import lombok.Builder;
import lombok.Data;

/**
 * 文件预览信息
 */
@Data
@Builder
public class PreviewVO {

    /** 文件分类: IMAGE/VIDEO/AUDIO/DOCUMENT/ARCHIVE/CODE/OTHER */
    private String category;

    /** 文件名 */
    private String fileName;

    /** MIME 类型 */
    private String mimeType;

    /** 文件大小(bytes) */
    private Long fileSize;

    /** 预览地址(可直连或走服务端流式输出) */
    private String url;

    /** 提示信息(如压缩包暂不支持预览) */
    private String message;
}