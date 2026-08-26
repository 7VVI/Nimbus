package com.nimbus.system.listener;

import com.nimbus.log.event.OperLogEvent;
import com.nimbus.system.mapper.NimbusOperLogMapper;
import com.nimbus.system.model.entity.NimbusOperLog;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * 操作审计日志消费者: 监听 OperLogEvent 异步落库, 失败不影响主流程
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OperLogEventListener {

    private final NimbusOperLogMapper nimbusOperLogMapper;

    @EventListener
    public void onOperLog(OperLogEvent event) {
        try {
            NimbusOperLog operLog = new NimbusOperLog();
            operLog.setTitle(event.getTitle());
            operLog.setBusinessType(event.getBusinessType());
            operLog.setMethod(event.getMethod());
            operLog.setRequestMethod(event.getRequestMethod());
            operLog.setOperUrl(event.getOperUrl());
            operLog.setOperName(event.getOperName());
            operLog.setOperUserId(event.getOperUserId());
            operLog.setOperIp(event.getOperIp());
            operLog.setOperParam(event.getOperParam());
            operLog.setJsonResult(event.getJsonResult());
            operLog.setStatus(event.getStatus());
            operLog.setErrorMsg(event.getErrorMsg());
            operLog.setOperTime(event.getOperTime());
            operLog.setCostTime(event.getCostTime());
            nimbusOperLogMapper.insert(operLog);
        } catch (Exception e) {
            log.warn("审计日志落库失败: {}", e.getMessage());
        }
    }
}