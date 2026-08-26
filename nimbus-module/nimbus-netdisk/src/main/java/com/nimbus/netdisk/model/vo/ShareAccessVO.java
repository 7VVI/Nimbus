package com.nimbus.netdisk.model.vo;

import com.nimbus.netdisk.model.entity.NimbusShare;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 访问分享结果
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ShareAccessVO {

    /** 分享信息 */
    private NimbusShare share;

    /** 首层目标列表 */
    private List<ShareItemVO> items;
}