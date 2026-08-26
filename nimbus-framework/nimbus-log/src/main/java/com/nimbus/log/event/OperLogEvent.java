package com.nimbus.log.event;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 操作日志事件, 由 {@code @OperLog} 切面发布, 消费者负责落库
 */
@Data
public class OperLogEvent implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 操作模块标题 */
    private String title;

    /** 业务操作类型 */
    private String businessType;

    /** 操作方法(类名.方法名) */
    private String method;

    /** 请求方式 */
    private String requestMethod;

    /** 请求地址 */
    private String operUrl;

    /** 操作人账号 */
    private String operName;

    /** 操作人用户 id */
    private Long operUserId;

    /** 操作人 IP */
    private String operIp;

    /** 请求参数 */
    private String operParam;

    /** 响应结果 */
    private String jsonResult;

    /** 状态: 0成功 1失败 */
    private Integer status;

    /** 错误信息 */
    private String errorMsg;

    /** 操作时间 */
    private LocalDateTime operTime;

    /** 耗时(毫秒) */
    private Long costTime;
}