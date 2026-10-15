package com.meiyetec.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("crm_ltv_snapshot")
public class CrmLtvSnapshot {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private Long brandId;
    private Long memberId;
    private BigDecimal historicalLtv;
    @TableField("predicted_ltv_30d")
    private BigDecimal predictedLtv30d;
    @TableField("predicted_ltv_90d")
    private BigDecimal predictedLtv90d;
    @TableField("predicted_ltv_365d")
    private BigDecimal predictedLtv365d;
    @TableField("predicted_ltv_lifetime")
    private BigDecimal predictedLtvLifetime;
    @TableField("repurchase_prob_30d")
    private BigDecimal repurchaseProb30d;
    @TableField("repurchase_prob_90d")
    private BigDecimal repurchaseProb90d;
    @TableField("repurchase_prob_180d")
    private BigDecimal repurchaseProb180d;
    /** BG/NBD/HISTORICAL/ML/RULE */
    private String modelType;
    private String modelVersion;
    private BigDecimal avgOrderValue;
    private BigDecimal purchaseFrequency;
    private Integer lastPurchaseDays;
    private Integer totalOrders;
    private BigDecimal auc;
    private BigDecimal recall;
    private BigDecimal precisionScore;
    private LocalDate snapshotDate;
    private Integer calcDurationMs;
    private String operatorName;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
