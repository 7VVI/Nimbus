package com.nimbus.log.aspect;

import com.nimbus.common.utils.JsonUtils;
import com.nimbus.common.utils.ServletUtils;
import com.nimbus.log.annotation.OperLog;
import com.nimbus.log.event.OperLogEvent;
import com.nimbus.security.model.LoginUser;
import com.nimbus.security.utils.LoginHelper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.validation.BindingResult;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.Arrays;

/**
 * 操作日志切面: 记录请求/响应/耗时/异常并发布 OperLogEvent, 不直接落库
 */
@Aspect
@Slf4j
public class OperLogAspect {

    /** 参数与结果最大保存长度 */
    private static final int MAX_LENGTH = 2000;

    private final ApplicationEventPublisher publisher;

    public OperLogAspect(ApplicationEventPublisher publisher) {
        this.publisher = publisher;
    }

    @Around("@annotation(operLog)")
    public Object around(ProceedingJoinPoint joinPoint, OperLog operLog) throws Throwable {
        long start = System.currentTimeMillis();
        try {
            Object result = joinPoint.proceed();
            publish(joinPoint, operLog, result, null, System.currentTimeMillis() - start);
            return result;
        } catch (Throwable e) {
            publish(joinPoint, operLog, null, e, System.currentTimeMillis() - start);
            throw e;
        }
    }

    private void publish(ProceedingJoinPoint joinPoint, OperLog operLog, Object result, Throwable e, long costTime) {
        try {
            OperLogEvent event = new OperLogEvent();
            event.setTitle(operLog.title());
            event.setBusinessType(operLog.businessType().name());
            event.setMethod(joinPoint.getSignature().getDeclaringTypeName() + "." + joinPoint.getSignature().getName());
            event.setStatus(e == null ? 0 : 1);
            event.setErrorMsg(e == null ? null : truncate(e.getMessage()));
            event.setOperTime(LocalDateTime.now());
            event.setCostTime(costTime);
            fillOperator(event);
            fillRequest(event, joinPoint, operLog, result);
            publisher.publishEvent(event);
        } catch (Exception ex) {
            // 日志记录失败不影响业务
            log.warn("操作日志记录失败: {}", ex.getMessage());
        }
    }

    private void fillOperator(OperLogEvent event) {
        event.setOperUserId(LoginHelper.getUserIdOrNull());
        LoginUser loginUser = LoginHelper.getLoginUserOrNull();
        if (loginUser != null) {
            event.setOperName(loginUser.getUsername());
        }
    }

    private void fillRequest(OperLogEvent event, ProceedingJoinPoint joinPoint, OperLog operLog, Object result) {
        HttpServletRequest request = ServletUtils.getRequest();
        if (request != null) {
            event.setRequestMethod(request.getMethod());
            event.setOperUrl(request.getRequestURI());
            event.setOperIp(ServletUtils.getClientIp(request));
        }
        if (operLog.saveRequestData()) {
            event.setOperParam(truncate(buildParam(joinPoint)));
        }
        if (operLog.saveResponseData() && result != null) {
            event.setJsonResult(truncate(JsonUtils.toJson(result)));
        }
    }

    /** 序列化请求参数, 过滤无法序列化的 Servlet/文件类型 */
    private String buildParam(JoinPoint joinPoint) {
        Object[] args = Arrays.stream(joinPoint.getArgs())
            .filter(arg -> !(arg instanceof HttpServletRequest || arg instanceof HttpServletResponse
                || arg instanceof MultipartFile || arg instanceof BindingResult))
            .toArray();
        return JsonUtils.toJson(args);
    }

    private String truncate(String text) {
        if (text == null) {
            return null;
        }
        return text.length() > MAX_LENGTH ? text.substring(0, MAX_LENGTH) : text;
    }
}