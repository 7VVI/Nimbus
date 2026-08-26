package com.nimbus.common.exception;

import lombok.Getter;

import java.io.Serial;

/**
 * 服务异常, 表示基础设施(存储/序列化等)调用失败, 由全局异常处理器统一转为失败响应
 */
@Getter
public class ServiceException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 错误码 */
    private final int code;

    public ServiceException(String message) {
        super(message);
        this.code = ErrorCode.INTERNAL_ERROR.getCode();
    }

    public ServiceException(String message, Throwable cause) {
        super(message, cause);
        this.code = ErrorCode.INTERNAL_ERROR.getCode();
    }
}