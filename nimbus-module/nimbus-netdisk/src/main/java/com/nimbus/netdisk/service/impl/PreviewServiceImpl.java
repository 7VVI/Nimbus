package com.nimbus.netdisk.service.impl;

import com.nimbus.netdisk.constant.NetdiskConstants;
import com.nimbus.netdisk.model.entity.NimbusFile;
import com.nimbus.netdisk.model.vo.PreviewVO;
import com.nimbus.netdisk.service.FileService;
import com.nimbus.netdisk.service.PreviewService;
import com.nimbus.storage.core.StorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 文件预览业务实现: 按扩展名分类, 直连地址优先, 本地存储走服务端流式输出
 */
@Service
@RequiredArgsConstructor
public class PreviewServiceImpl implements PreviewService {

    private final StorageService storageService;

    private final FileService fileService;

    @Override
    public PreviewVO getPreview(Long userId, Long fileId) {
        NimbusFile file = fileService.getOwnedFile(userId, fileId, NetdiskConstants.FILE_STATUS_NORMAL);
        String category = fileService.categoryOf(file.getFileExt());
        if (NetdiskConstants.CATEGORY_ARCHIVE.equals(category)) {
            return PreviewVO.builder()
                .category(category)
                .fileName(file.getFileName())
                .mimeType(file.getMimeType())
                .fileSize(file.getFileSize())
                .message("压缩包暂不支持在线预览, 请下载后解压查看")
                .build();
        }
        // 直连地址优先(对象存储预签名), 本地存储返回服务端流式输出地址
        String url = storageService.accessUrl(file.getStorageKey(), file.getFileName(), true);
        if (url == null) {
            url = "/api/netdisk/preview/" + fileId + "/content";
        }
        return PreviewVO.builder()
            .category(category)
            .fileName(file.getFileName())
            .mimeType(file.getMimeType())
            .fileSize(file.getFileSize())
            .url(url)
            .build();
    }
}