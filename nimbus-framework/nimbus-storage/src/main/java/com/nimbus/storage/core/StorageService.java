package com.nimbus.storage.core;

import java.io.InputStream;

/**
 * 对象存储服务抽象
 * <p>
 * 业务层只依赖该接口, 本地磁盘与 S3 兼容存储通过配置切换, 便于未来扩展其他存储实现
 */
public interface StorageService {

    /** 写入分片, 分片内容由服务端接收后落盘 */
    void putChunk(String uploadId, int chunkIndex, byte[] data);

    /** 合并指定数量分片为完整对象, 返回对象 key */
    String mergeChunks(String uploadId, int chunkCount, String objectKey);

    /** 清理分片临时数据(合并成功后或上传取消时调用) */
    void deleteUpload(String uploadId);

    /** 对象是否存在 */
    boolean exists(String key);

    /** 读取对象全部内容 */
    byte[] get(String key);

    /** 打开对象输入流, 用于流式下载/预览 */
    InputStream open(String key);

    /** 删除对象 */
    void delete(String key);

    /**
     * 生成对象访问地址
     *
     * @param inline    true 内联预览, false 附件下载
     * @return 预签名/公开地址; 不支持时(如本地磁盘)返回 null, 由调用方走服务端流式输出
     */
    String accessUrl(String key, String fileName, boolean inline);
}