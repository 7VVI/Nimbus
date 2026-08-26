package com.nimbus.storage.core;

import com.nimbus.common.exception.ServiceException;
import com.nimbus.storage.config.StorageProperties;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.AbortMultipartUploadRequest;
import software.amazon.awssdk.services.s3.model.CompletedMultipartUpload;
import software.amazon.awssdk.services.s3.model.CompletedPart;
import software.amazon.awssdk.services.s3.model.CompleteMultipartUploadRequest;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.CreateMultipartUploadRequest;
import software.amazon.awssdk.services.s3.model.CreateMultipartUploadResponse;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.UploadPartCopyRequest;
import software.amazon.awssdk.services.s3.model.UploadPartCopyResponse;
import software.amazon.awssdk.services.s3.model.UploadPartRequest;
import software.amazon.awssdk.services.s3.model.UploadPartResponse;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

import java.io.InputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * S3 协议存储实现: 统一适配 MinIO / 阿里云 OSS / 腾讯云 COS 等兼容存储
 * <p>
 * 分片流: 分片作为 MultipartUpload 的 Part 上传到临时对象, 合并时通过
 * UploadPartCopy 组装为最终对象, 避免服务端下载再上传的带宽消耗。
 * (多副本部署时 Part 状态保存在进程内, 如需跨实例共享可迁移至 Redis)
 */
public class OssStorageService implements StorageService {

    /** 预签名地址有效期 */
    private static final Duration PRESIGN_TTL = Duration.ofMinutes(15);

    /** 临时分片对象前缀 */
    private static final String TMP_PREFIX = "tmp/";

    private final StorageProperties.Oss config;

    private final S3Client s3;

    private final S3Presigner presigner;

    /** 上传任务分片状态: 任务 uploadId -> (minio uploadId, partNumber -> etag) */
    private final Map<String, PartContext> partContexts = new ConcurrentHashMap<>();

    public OssStorageService(StorageProperties config) {
        this.config = config.getOss();
        StaticCredentialsProvider credentials = StaticCredentialsProvider.create(
            AwsBasicCredentials.create(this.config.getAccessKey(), this.config.getSecretKey()));
        Region region = Region.of(this.config.getRegion() == null || this.config.getRegion().isBlank()
            ? "us-east-1" : this.config.getRegion());
        S3Configuration serviceConfig = S3Configuration.builder()
            .pathStyleAccessEnabled(true)
            .chunkedEncodingEnabled(false)
            .build();
        // MinIO 等自建存储使用路径风格, 云厂商虚拟主机风格可复用 hmoob OssClient 的适配思路
        this.s3 = S3Client.builder()
            .credentialsProvider(credentials)
            .endpointOverride(URI.create(endpointUrl()))
            .region(region)
            .serviceConfiguration(serviceConfig)
            .build();
        this.presigner = S3Presigner.builder()
            .credentialsProvider(credentials)
            .endpointOverride(URI.create(endpointUrl()))
            .region(region)
            .serviceConfiguration(serviceConfig)
            .build();
        ensureBucket();
    }

    @Override
    public void putChunk(String uploadId, int chunkIndex, byte[] data) {
        PartContext context = partContexts.computeIfAbsent(uploadId, this::initMultipartUpload);
        try {
            UploadPartResponse response = s3.uploadPart(UploadPartRequest.builder()
                .bucket(config.getBucketName())
                .key(tmpKey(uploadId))
                .uploadId(context.getMinioUploadId())
                .partNumber(chunkIndex + 1)
                .build(), RequestBody.fromBytes(data));
            context.getEtags().put(chunkIndex + 1, response.eTag());
            context.getSizes().put(chunkIndex + 1, (long) data.length);
        } catch (S3Exception e) {
            throw new ServiceException("分片上传失败: uploadId=" + uploadId + ", chunk=" + chunkIndex, e);
        }
    }

    @Override
    public String mergeChunks(String uploadId, int chunkCount, String objectKey) {
        PartContext context = partContexts.remove(uploadId);
        if (context == null) {
            throw new ServiceException("分片状态不存在或已过期: uploadId=" + uploadId);
        }
        try {
            // 临时对象各分片按字节区间复制到最终对象, 等价于服务端拼接
            CreateMultipartUploadResponse merge = s3.createMultipartUpload(CreateMultipartUploadRequest.builder()
                .bucket(config.getBucketName())
                .key(objectKey)
                .build());
            String mergeUploadId = merge.uploadId();
            List<CompletedPart> parts = new ArrayList<>();
            long offset = 0;
            for (int i = 1; i <= chunkCount; i++) {
                String etag = context.getEtags().get(i);
                Long size = context.getSizes().get(i);
                if (etag == null || size == null) {
                    throw new ServiceException("分片缺失: uploadId=" + uploadId + ", chunk=" + (i - 1));
                }
                UploadPartCopyResponse copy = s3.uploadPartCopy(UploadPartCopyRequest.builder()
                    .sourceBucket(config.getBucketName())
                    .sourceKey(tmpKey(uploadId))
                    .destinationBucket(config.getBucketName())
                    .destinationKey(objectKey)
                    .uploadId(mergeUploadId)
                    .partNumber(i)
                    .copySourceRange("bytes=" + offset + "-" + (offset + size - 1))
                    .build());
                parts.add(CompletedPart.builder()
                    .partNumber(i)
                    .eTag(copy.copyPartResult().eTag())
                    .build());
                offset += size;
            }
            s3.completeMultipartUpload(CompleteMultipartUploadRequest.builder()
                .bucket(config.getBucketName())
                .key(objectKey)
                .uploadId(mergeUploadId)
                .multipartUpload(CompletedMultipartUpload.builder().parts(parts).build())
                .build());
            // 临时对象已无引用, 直接清理
            s3.deleteObject(DeleteObjectRequest.builder()
                .bucket(config.getBucketName())
                .key(tmpKey(uploadId))
                .build());
        } catch (S3Exception e) {
            throw new ServiceException("分片合并失败: uploadId=" + uploadId, e);
        } finally {
            partContexts.remove(uploadId);
        }
        return objectKey;
    }

    @Override
    public void deleteUpload(String uploadId) {
        PartContext context = partContexts.remove(uploadId);
        if (context == null || context.getMinioUploadId() == null) {
            return;
        }
        try {
            s3.abortMultipartUpload(AbortMultipartUploadRequest.builder()
                .bucket(config.getBucketName())
                .key(tmpKey(uploadId))
                .uploadId(context.getMinioUploadId())
                .build());
        } catch (S3Exception ignored) {
            // 已合并或已中止时忽略
        }
    }

    @Override
    public boolean exists(String key) {
        try {
            s3.headObject(HeadObjectRequest.builder().bucket(config.getBucketName()).key(key).build());
            return true;
        } catch (S3Exception e) {
            return false;
        }
    }

    @Override
    public byte[] get(String key) {
        try {
            return s3.getObjectAsBytes(GetObjectRequest.builder()
                .bucket(config.getBucketName())
                .key(key)
                .build()).asByteArray();
        } catch (S3Exception e) {
            throw new ServiceException("存储对象读取失败: " + key, e);
        }
    }

    @Override
    public InputStream open(String key) {
        try {
            return s3.getObject(GetObjectRequest.builder()
                .bucket(config.getBucketName())
                .key(key)
                .build());
        } catch (S3Exception e) {
            throw new ServiceException("存储对象打开失败: " + key, e);
        }
    }

    @Override
    public void delete(String key) {
        try {
            s3.deleteObject(DeleteObjectRequest.builder()
                .bucket(config.getBucketName())
                .key(key)
                .build());
        } catch (S3Exception e) {
            throw new ServiceException("存储对象删除失败: " + key, e);
        }
    }

    @Override
    public String accessUrl(String key, String fileName, boolean inline) {
        String disposition = (inline ? "inline" : "attachment")
            + "; filename=\"" + URLEncoder.encode(fileName, StandardCharsets.UTF_8).replace("+", "%20") + "\"";
        GetObjectPresignRequest request = GetObjectPresignRequest.builder()
            .signatureDuration(PRESIGN_TTL)
            .getObjectRequest(GetObjectRequest.builder()
                .bucket(config.getBucketName())
                .key(key)
                .responseContentDisposition(disposition)
                .build())
            .build();
        try {
            return presigner.presignGetObject(request).url().toString();
        } catch (Exception e) {
            throw new ServiceException("预签名地址生成失败: " + key, e);
        }
    }

    /** 初始化分片上传, 仅在缺失时创建 */
    private PartContext initMultipartUpload(String uploadId) {
        CreateMultipartUploadResponse response = s3.createMultipartUpload(CreateMultipartUploadRequest.builder()
            .bucket(config.getBucketName())
            .key(tmpKey(uploadId))
            .build());
        return new PartContext(response.uploadId());
    }

    private void ensureBucket() {
        try {
            s3.headBucket(HeadBucketRequest.builder().bucket(config.getBucketName()).build());
        } catch (S3Exception e) {
            try {
                s3.createBucket(CreateBucketRequest.builder().bucket(config.getBucketName()).build());
            } catch (S3Exception ignored) {
                // 并发创建时已存在则忽略
            }
        }
    }

    private String tmpKey(String uploadId) {
        return TMP_PREFIX + uploadId;
    }

    private String endpointUrl() {
        return (config.isHttps() ? "https://" : "http://") + config.getEndpoint();
    }

    /** 单次分片任务的分片状态 */
    private static class PartContext {

        private final String minioUploadId;

        /** partNumber -> etag */
        private final Map<Integer, String> etags = new ConcurrentHashMap<>();

        /** partNumber -> 分片字节数 */
        private final Map<Integer, Long> sizes = new ConcurrentHashMap<>();

        private PartContext(String minioUploadId) {
            this.minioUploadId = minioUploadId;
        }

        public String getMinioUploadId() {
            return minioUploadId;
        }

        public Map<Integer, String> getEtags() {
            return etags;
        }

        public Map<Integer, Long> getSizes() {
            return sizes;
        }
    }
}