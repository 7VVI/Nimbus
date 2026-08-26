package com.nimbus.common.model;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 通用分页查询参数
 */
@Data
public class PageQuery implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 默认页码 */
    public static final int DEFAULT_PAGE_NUM = 1;
    /** 默认每页条数 */
    public static final int DEFAULT_PAGE_SIZE = 10;

    /** 页码, 从 1 开始 */
    @Min(value = 1, message = "页码不能小于1")
    private Integer pageNum = DEFAULT_PAGE_NUM;

    /** 每页条数, 最大 100 */
    @Min(value = 1, message = "每页条数不能小于1")
    @Max(value = 100, message = "每页条数不能大于100")
    private Integer pageSize = DEFAULT_PAGE_SIZE;
}