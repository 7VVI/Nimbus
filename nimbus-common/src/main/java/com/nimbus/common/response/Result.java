package com.nimbus.common.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;

/**
 * 统一响应体
 *
 * @param <T> 数据类型
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Result<T> implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 成功状态码 */
    public static final int SUCCESS = 200;
    /** 失败状态码 */
    public static final int FAIL = 500;

    /** 状态码 */
    private int code;
    /** 提示信息 */
    private String msg;
    /** 业务数据 */
    private T data;

    public static <T> Result<T> ok() {
        return new Result<>(SUCCESS, "操作成功", null);
    }

    public static <T> Result<T> ok(T data) {
        return new Result<>(SUCCESS, "操作成功", data);
    }

    public static <T> Result<T> ok(String msg, T data) {
        return new Result<>(SUCCESS, msg, data);
    }

    public static <T> Result<T> fail() {
        return new Result<>(FAIL, "操作失败", null);
    }

    public static <T> Result<T> fail(String msg) {
        return new Result<>(FAIL, msg, null);
    }

    public static <T> Result<T> fail(int code, String msg) {
        return new Result<>(code, msg, null);
    }

    public boolean isSuccess() {
        return this.code == SUCCESS;
    }
}