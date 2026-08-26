package com.nimbus.system.model.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.nimbus.mybatis.model.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 操作审计日志表, 由 OperLogEvent 消费者写入
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("nimbus_oper_log")
public class NimbusOperLog extends BaseEntity {

    /** 操作模块标题 */
    private String title;

    /** 业务操作类型 */
    private String businessType;

    /** 操作方法 */
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