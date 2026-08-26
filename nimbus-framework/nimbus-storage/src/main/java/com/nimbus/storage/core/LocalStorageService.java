package com.nimbus.storage.core;

import com.nimbus.common.exception.ServiceException;
import com.nimbus.storage.config.StorageProperties;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Comparator;
import java.util.stream.Stream;

/**
 * 本地磁盘存储实现(默认), 分片暂存 tmp 目录, 合并后写入对象目录
 * <p>
 * 不支持预签名地址, accessUrl 返回 null, 由业务层走服务端流式输出
 */
public class LocalStorageService implements StorageService {

    /** 分片暂存目录名 */
    private static final String TMP_DIR = "tmp";

    private final Path root;

    public LocalStorageService(StorageProperties config) {
        this.root = Path.of(config.getLocal().getBasePath()).toAbsolutePath().normalize();
        try {
            Files.createDirectories(root);
        } catch (IOException e) {
            throw new ServiceException("本地存储目录创建失败: " + root, e);
        }
    }

    @Override
    public void putChunk(String uploadId, int chunkIndex, byte[] data) {
        Path chunk = chunkPath(uploadId, chunkIndex);
        try {
            Files.createDirectories(chunk.getParent());
            Files.write(chunk, data);
        } catch (IOException e) {
            throw new ServiceException("分片写入失败: uploadId=" + uploadId + ", chunk=" + chunkIndex, e);
        }
    }

    @Override
    public String mergeChunks(String uploadId, int chunkCount, String objectKey) {
        Path target = objectPath(objectKey);
        try {
            Files.createDirectories(target.getParent());
            // 顺序拼接分片, 分片缺失时报错
            for (int i = 0; i < chunkCount; i++) {
                Path chunk = chunkPath(uploadId, i);
                if (!Files.exists(chunk)) {
                    throw new ServiceException("分片缺失: uploadId=" + uploadId + ", chunk=" + i);
                }
                append(target, chunk);
            }
        } catch (IOException e) {
            throw new ServiceException("分片合并失败: uploadId=" + uploadId, e);
        } finally {
            deleteUpload(uploadId);
        }
        return objectKey;
    }

    @Override
    public void deleteUpload(String uploadId) {
        Path dir = root.resolve(TMP_DIR).resolve(uploadId);
        if (!Files.exists(dir)) {
            return;
        }
        try (Stream<Path> walk = Files.walk(dir)) {
            walk.sorted(Comparator.reverseOrder()).forEach(p -> {
                try {
                    Files.deleteIfExists(p);
                } catch (IOException ignored) {
                    // 清理失败不影响主流程, 残留由后续周期任务回收
                }
            });
        } catch (IOException ignored) {
        }
    }

    @Override
    public boolean exists(String key) {
        return Files.exists(objectPath(key));
    }

    @Override
    public byte[] get(String key) {
        try {
            return Files.readAllBytes(objectPath(key));
        } catch (IOException e) {
            throw new ServiceException("存储对象读取失败: " + key, e);
        }
    }

    @Override
    public InputStream open(String key) {
        try {
            return Files.newInputStream(objectPath(key));
        } catch (IOException e) {
            throw new ServiceException("存储对象打开失败: " + key, e);
        }
    }

    @Override
    public void delete(String key) {
        try {
            Files.deleteIfExists(objectPath(key));
        } catch (IOException e) {
            throw new ServiceException("存储对象删除失败: " + key, e);
        }
    }

    @Override
    public String accessUrl(String key, String fileName, boolean inline) {
        // 本地磁盘不暴露直连地址, 由业务层流式输出
        return null;
    }

    private Path chunkPath(String uploadId, int chunkIndex) {
        return root.resolve(TMP_DIR).resolve(uploadId).resolve(chunkIndex + ".part");
    }

    private Path objectPath(String key) {
        return root.resolve(key).normalize();
    }

    /** 将 chunk 内容追加到 target 末尾 */
    private void append(Path target, Path chunk) throws IOException {
        try (InputStream in = Files.newInputStream(chunk);
             OutputStream out = Files.newOutputStream(target, StandardOpenOption.CREATE, StandardOpenOption.APPEND)) {
            in.transferTo(out);
        }
    }
}