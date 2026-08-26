package com.nimbus.system.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.nimbus.common.model.PageQuery;
import com.nimbus.common.model.PageResult;
import com.nimbus.mybatis.utils.PageUtils;
import com.nimbus.system.mapper.NimbusOperLogMapper;
import com.nimbus.system.model.entity.NimbusOperLog;
import com.nimbus.system.service.OperLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 操作审计日志业务实现
 */
@Service
@RequiredArgsConstructor
public class OperLogServiceImpl implements OperLogService {

    private final NimbusOperLogMapper nimbusOperLogMapper;

    @Override
    public PageResult<NimbusOperLog> page(PageQuery query) {
        Page<NimbusOperLog> page = nimbusOperLogMapper.selectPage(PageUtils.toPage(query),
            new LambdaQueryWrapper<NimbusOperLog>().orderByDesc(NimbusOperLog::getOperTime));
        return PageUtils.toResult(page);
    }

    @Override
    public long clean() {
        return nimbusOperLogMapper.delete(null);
    }
}