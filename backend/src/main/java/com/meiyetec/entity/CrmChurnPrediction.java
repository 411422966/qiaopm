package com.meiyetec.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("crm_churn_prediction")
public class CrmChurnPrediction {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private Long brandId;
    private Long memberId;
    private Integer totalOrders;
    private BigDecimal historicalLtv;
    private BigDecimal avgOrderValue;
    private BigDecimal monthlyFreq;
    private Integer lastPurchaseDays;
    private Integer tenureDays;
    private Integer complaintCount;
    private BigDecimal reviewAvgRating;
    private BigDecimal churnScore;
    private String riskLevel;
    private LocalDate predictedLostDate;
    private String modelCode;
    private String modelVersion;
    private LocalDate snapshotDate;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}