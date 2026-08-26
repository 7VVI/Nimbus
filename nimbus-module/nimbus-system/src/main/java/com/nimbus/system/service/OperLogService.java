package com.nimbus.system.service;

import com.nimbus.common.model.PageQuery;
import com.nimbus.common.model.PageResult;
import com.nimbus.system.model.entity.NimbusOperLog;

/**
 * 操作审计日志业务接口
 */
public interface OperLogService {

    /** 分页查询审计日志 */
    PageResult<NimbusOperLog> page(PageQuery query);

    /** 清空全部审计日志, 返回删除数量 */
    long clean();
}