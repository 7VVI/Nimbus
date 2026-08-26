package com.nimbus.netdisk.service;

import com.nimbus.netdisk.model.dto.UploadInitDTO;
import com.nimbus.netdisk.model.dto.UploadMergeDTO;
import com.nimbus.netdisk.model.entity.NimbusFile;
import com.nimbus.netdisk.model.vo.UploadInitVO;
import org.springframework.web.multipart.MultipartFile;

/**
 * 上传业务接口: 秒传检测 + 分片上传(断点续传) + 新版本上传
 */
public interface UploadService {

    /** 初始化上传: 秒传检测, 未命中创建分片任务, 返回已上传分片支持续传 */
    UploadInitVO init(Long userId, UploadInitDTO dto);

    /** 上传单个分片 */
    void uploadChunk(String uploadId, int chunkIndex, byte[] data);

    /** 合并分片, 创建文件记录(或提交新版本) */
    NimbusFile merge(Long userId, UploadMergeDTO dto);

    /** 取消上传, 清理分片与任务 */
    void cancel(String uploadId);

    /** 小文件整体上传(≤ 分片大小) */
    NimbusFile singleUpload(Long userId, MultipartFile file, Long folderId);
}