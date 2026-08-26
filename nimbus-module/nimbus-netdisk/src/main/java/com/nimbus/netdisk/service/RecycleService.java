package com.nimbus.netdisk.service;

import com.nimbus.common.model.PageQuery;
import com.nimbus.common.model.PageResult;
import com.nimbus.netdisk.model.vo.RecycleItemVO;

/**
 * 回收站业务接口
 */
public interface RecycleService {

    /** 回收站列表(文件与文件夹统一分页) */
    PageResult<RecycleItemVO> page(Long userId, PageQuery query);

    /** 恢复, 原位置不可用时回退到根目录 */
    void restore(Long userId, Integer targetType, Long id);

    /** 彻底删除并释放配额 */
    void purge(Long userId, Integer targetType, Long id);

    /** 清空回收站, 返回清除条数 */
    long clean(Long userId);
}