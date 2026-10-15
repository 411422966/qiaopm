package com.meiyetec.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * R3.0-G P3-2：第三方平台券/团购（美团/抖音）
 */
@Data
@TableName("platform_deal")
public class PlatformDeal {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private Long storeId;
    /** MEITUAN/DOUYIN */
    private String platform;
    private String platformDealId;
    private String dealName;
    private BigDecimal originalPrice;
    private BigDecimal salePrice;
    private Integer stock;
    /** PENDING/SYNCED/FAILED */
    private String syncStatus;
    private LocalDateTime syncAt;
    private LocalDateTime lastPullAt;
    private Integer enabled;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
    @TableLogic
    private Integer deleted;
}