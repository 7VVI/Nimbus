package com.nimbus.mybatis.utils;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.nimbus.common.model.PageQuery;
import com.nimbus.common.model.PageResult;

import java.util.List;
import java.util.function.Function;

/**
 * MyBatis-Plus 分页对象与通用分页模型转换
 */
public final class PageUtils {

    private PageUtils() {
    }

    /** PageQuery 转 MyBatis-Plus Page */
    public static <T> Page<T> toPage(PageQuery query) {
        return new Page<>(query.getPageNum(), query.getPageSize());
    }

    /** MyBatis-Plus Page 转 PageResult */
    public static <T> PageResult<T> toResult(Page<T> page) {
        return PageResult.of(page.getTotal(), page.getRecords());
    }

    /** MyBatis-Plus Page 转 PageResult 并映射记录类型 */
    public static <T, R> PageResult<R> toResult(Page<T> page, Function<T, R> mapper) {
        List<R> records = page.getRecords().stream().map(mapper).toList();
        return PageResult.of(page.getTotal(), records);
    }
}