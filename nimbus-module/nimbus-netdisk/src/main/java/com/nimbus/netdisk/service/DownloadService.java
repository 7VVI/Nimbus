package com.nimbus.netdisk.service;

import com.nimbus.netdisk.model.dto.BatchDownloadDTO;
import com.nimbus.netdisk.model.entity.NimbusFile;

import java.io.InputStream;
import java.util.LinkedHashMap;

/**
 * 下载业务接口
 */
public interface DownloadService {

    /** 获取用户自己的正常文件 */
    NimbusFile getOwnedFile(Long userId, Long fileId);

    /** 打开文件内容流 */
    InputStream openFile(NimbusFile file);

    /** 直连/预签名地址, 不支持时返回 null(走服务端流式输出) */
    String accessUrl(NimbusFile file, boolean inline);

    /** 批量下载收集: 返回 相对路径 -> 文件, 文件夹展开为全部子文件 */
    LinkedHashMap<String, NimbusFile> collectBatchFiles(Long userId, BatchDownloadDTO dto);
}