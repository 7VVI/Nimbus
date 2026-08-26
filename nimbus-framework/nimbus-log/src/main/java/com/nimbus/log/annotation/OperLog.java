package com.nimbus.log.annotation;

import com.nimbus.log.enums.BusinessType;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 操作日志注解, 标注在 Controller 方法上, 由切面记录请求并发布 OperLogEvent
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface OperLog {

    /** 操作模块标题, 如 "文件管理" */
    String title() default "";

    /** 业务操作类型 */
    BusinessType businessType() default BusinessType.OTHER;

    /** 是否记录请求参数 */
    boolean saveRequestData() default true;

    /** 是否记录响应结果 */
    boolean saveResponseData() default true;
}