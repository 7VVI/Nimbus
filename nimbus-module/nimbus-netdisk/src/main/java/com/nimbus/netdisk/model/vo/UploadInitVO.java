package com.nimbus.netdisk.model.vo;

import com.nimbus.netdisk.model.entity.NimbusFile;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 上传初始化结果
 */
@Data
public class UploadInitVO {

    /** 是否秒传成功(命中相同哈希) */
    private boolean instant;

    /** 秒传成功时的文件记录 */
    private NimbusFile file;

    /** 上传任务 id */
    private String uploadId;

    /** 分片总数 */
    private Integer chunkCount;

    /** 分片大小(bytes) */
    private Long chunkSize;

    /** 已上传分片序号(断点续传), 需重点上传缺失分片 */
    private List<Integer> existChunks = new ArrayList<>();
}