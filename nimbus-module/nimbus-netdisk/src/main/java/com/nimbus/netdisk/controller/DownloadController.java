package com.nimbus.netdisk.controller;

import com.nimbus.common.response.Result;
import com.nimbus.log.annotation.OperLog;
import com.nimbus.log.enums.BusinessType;
import com.nimbus.netdisk.model.dto.BatchDownloadDTO;
import com.nimbus.netdisk.model.entity.NimbusFile;
import com.nimbus.netdisk.service.DownloadService;
import com.nimbus.security.utils.LoginHelper;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * 下载接口: 单文件直链/流式 + 批量打包(文件夹展开为子文件)
 */
@RestController
@RequestMapping("/api/netdisk/download")
@RequiredArgsConstructor
public class DownloadController {

    private final DownloadService downloadService;

    /** 单文件下载, 对象存储返回预签名地址, 本地存储服务端流式输出 */
    @GetMapping("/file/{fileId}")
    public ResponseEntity<?> download(@PathVariable Long fileId) {
        NimbusFile file = downloadService.getOwnedFile(LoginHelper.getUserId(), fileId);
        String url = downloadService.accessUrl(file, false);
        if (url != null) {
            return ResponseEntity.status(HttpStatus.FOUND).location(URI.create(url)).build();
        }
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
        headers.setContentDisposition(ContentDisposition.attachment()
            .filename(file.getFileName(), StandardCharsets.UTF_8).build());
        if (file.getFileSize() != null) {
            headers.setContentLength(file.getFileSize());
        }
        return new ResponseEntity<>(new InputStreamResource(downloadService.openFile(file)), headers, HttpStatus.OK);
    }

    /** 批量下载, 服务端实时打包 zip 流式返回 */
    @OperLog(title = "批量下载", businessType = BusinessType.EXPORT, saveRequestData = false, saveResponseData = false)
    @PostMapping("/batch")
    public ResponseEntity<StreamingResponseBody> batch(@Valid @RequestBody BatchDownloadDTO dto) {
        Map<String, NimbusFile> files = downloadService.collectBatchFiles(LoginHelper.getUserId(), dto);
        String name = "nimbus_" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss")) + ".zip";
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
        headers.setContentDisposition(ContentDisposition.attachment()
            .filename(name, StandardCharsets.UTF_8).build());
        return ResponseEntity.ok().headers(headers).body(out -> {
            try (ZipOutputStream zip = new ZipOutputStream(out)) {
                for (Map.Entry<String, NimbusFile> entry : files.entrySet()) {
                    zip.putNextEntry(new ZipEntry(entry.getKey()));
                    try (InputStream in = downloadService.openFile(entry.getValue())) {
                        in.transferTo(zip);
                    }
                    zip.closeEntry();
                }
            }
        });
    }
}