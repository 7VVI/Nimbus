package com.nimbus.common.exception;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 通用错误码
 * <p>
 * 约定: 200 成功; 4xx 客户端错误; 5xx 服务端错误; 1xxx 业务错误
 */
@Getter
@AllArgsConstructor
public enum ErrorCode {

    SUCCESS(200, "操作成功"),

    BAD_REQUEST(400, "请求参数错误"),
    UNAUTHORIZED(401, "登录状态已失效，请重新登录"),
    FORBIDDEN(403, "没有访问权限"),
    NOT_FOUND(404, "请求资源不存在"),
    METHOD_NOT_ALLOWED(405, "请求方式不支持"),

    INTERNAL_ERROR(500, "系统繁忙，请稍后重试"),

    BUSINESS_ERROR(1000, "业务处理失败"),
    DATA_NOT_EXIST(1001, "数据不存在"),
    DATA_ALREADY_EXIST(1002, "数据已存在"),

    QUOTA_EXCEEDED(1101, "存储空间不足"),
    FILE_NOT_EXIST(1102, "文件不存在或已删除"),
    FOLDER_NOT_EXIST(1103, "文件夹不存在或已删除"),
    NAME_CONFLICT(1104, "同名文件或文件夹已存在"),
    SHARE_NOT_EXIST(1105, "分享不存在或已取消"),
    SHARE_EXPIRED(1106, "分享已过期"),
    SHARE_PASSWORD_ERROR(1107, "提取码错误"),
    UPLOAD_TASK_NOT_EXIST(1108, "上传任务不存在或已过期"),
    CHUNK_INCOMPLETE(1109, "分片未全部上传完成"),
    SHARE_OPERATION_FORBIDDEN(1110, "该分享未开放此操作");

    /** 错误码 */
    private final int code;
    /** 错误信息 */
    private final String message;
}