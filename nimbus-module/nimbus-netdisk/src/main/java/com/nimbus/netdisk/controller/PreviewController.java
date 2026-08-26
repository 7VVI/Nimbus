package com.nimbus.netdisk.controller;

import com.nimbus.common.response.Result;
import com.nimbus.netdisk.constant.NetdiskConstants;
import com.nimbus.netdisk.model.entity.NimbusFile;
import com.nimbus.netdisk.model.vo.PreviewVO;
import com.nimbus.netdisk.service.FileService;
import com.nimbus.netdisk.service.PreviewService;
import com.nimbus.security.utils.LoginHelper;
import com.nimbus.storage.core.StorageService;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.io.InputStream;

/**
 * 预览接口: 预览信息 + 内容流式输出(支持 Range 请求, 供音视频拖动进度)
 * <p>
 * 内容输出直接写 HttpServletResponse, 避免 ResponseEntity 转换器与输入流生命周期的坑
 */
@RestController
@RequestMapping("/api/netdisk/preview")
@RequiredArgsConstructor
public class PreviewController {

    /** 单次 Range 响应最大字节数, 防止超大视频打爆内存 */
    private static final int MAX_RANGE_LENGTH = 8 * 1024 * 1024;

    private final PreviewService previewService;

    private final FileService fileService;

    private final StorageService storageService;

    /** 预览信息 */
    @GetMapping("/{fileId}")
    public Result<PreviewVO> preview(@PathVariable Long fileId) {
        return Result.ok(previewService.getPreview(LoginHelper.getUserId(), fileId));
    }

    /** 内容流式输出(内联预览), 支持 Range */
    @GetMapping("/{fileId}/content")
    public void content(@PathVariable Long fileId,
                        @RequestHeader(value = "Range", required = false) String rangeHeader,
                        HttpServletResponse response) throws IOException {
        NimbusFile file = fileService.getOwnedFile(LoginHelper.getUserId(), fileId, NetdiskConstants.FILE_STATUS_NORMAL);
        String mimeType = file.getMimeType() == null ? MediaType.APPLICATION_OCTET_STREAM_VALUE : file.getMimeType();
        long size = file.getFileSize() == null ? 0 : file.getFileSize();
        response.setHeader("Accept-Ranges", "bytes");
        response.setContentType(mimeType);
        if (size > 0) {
            response.setContentLengthLong(size);
        }

        Range range = parseRange(rangeHeader, size);
        if (range == null) {
            // 整文件输出: 直接流向响应流(由 try-with-resources 保证关闭)
            try (InputStream in = storageService.open(file.getStorageKey())) {
                in.transferTo(response.getOutputStream());
            }
            response.getOutputStream().flush();
            return;
        }
        // Range 区间: 定位到起始字节, 读取区间内容
        try (InputStream in = storageService.open(file.getStorageKey())) {
            in.skipNBytes(range.start());
            int length = (int) Math.min(range.length(), MAX_RANGE_LENGTH);
            byte[] data = in.readNBytes(length);
            response.setStatus(HttpServletResponse.SC_PARTIAL_CONTENT);
            response.setHeader(HttpHeaders.CONTENT_RANGE, "bytes " + range.start() + "-"
                + (range.start() + data.length - 1) + "/" + size);
            response.setContentLength(data.length);
            response.getOutputStream().write(data);
            response.getOutputStream().flush();
        }
    }

    /** 解析 Range: bytes=start-end | bytes=start- */
    private Range parseRange(String header, long size) {
        if (header == null || !header.startsWith("bytes=")) {
            return null;
        }
        String value = header.substring("bytes=".length()).trim();
        int dash = value.indexOf('-');
        if (dash < 0) {
            return null;
        }
        String startText = value.substring(0, dash).trim();
        String endText = value.substring(dash + 1).trim();
        long start;
        long end;
        if (startText.isEmpty()) {
            // 最后 N 字节
            long suffix = Long.parseLong(endText);
            start = Math.max(0, size - suffix);
            end = size - 1;
        } else {
            start = Long.parseLong(startText);
            end = endText.isEmpty() ? size - 1 : Math.min(Long.parseLong(endText), size - 1);
        }
        if (start > end || start >= size) {
            return null;
        }
        return new Range(start, end - start + 1);
    }

    private record Range(long start, long length) {
    }
}