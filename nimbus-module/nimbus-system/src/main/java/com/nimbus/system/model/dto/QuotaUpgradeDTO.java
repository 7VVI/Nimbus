package com.nimbus.system.model.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

/**
 * 配额升级入参
 */
@Data
public class QuotaUpgradeDTO {

    /** 新的总容量(bytes), 仅允许不小于当前总容量 */
    @NotNull(message = "目标容量不能为空")
    @Positive(message = "目标容量必须大于0")
    private Long totalSize;
}