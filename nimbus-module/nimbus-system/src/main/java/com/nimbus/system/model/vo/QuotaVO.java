package com.nimbus.system.model.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 用户存储配额信息
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class QuotaVO {

    /** 总容量(bytes) */
    private Long totalSize;

    /** 已用容量(bytes) */
    private Long usedSize;

    /** 剩余容量(bytes) */
    private Long remainSize;
}