package com.meiyetec.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("ltv_training_feature")
public class LtvTrainingFeature {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long tenantId;
    private Long memberId;
    private LocalDate featureDate;

    // RFM 基础
    private Integer recencyDays;
    private Integer frequencyTotal;
    private Integer frequency90d;
    private BigDecimal monetaryTotal;
    private BigDecimal monetary90d;
    private BigDecimal aov;
    private Integer avgIntervalDays;

    // 长期特征
    private Integer tenureDays;
    private String memberAgeGroup;
    private String lifecycleStage;

    // 消费模式
    private Integer serviceCategoryCount;
    private Long preferredCategoryId;
    private Long preferredEmployeeId;
    private Long preferredStoreId;
    private Integer preferredHour;
    private BigDecimal weekendRatio;
    private BigDecimal holidayRatio;

    // 钱包/套餐
    private BigDecimal walletBalance;
    private Integer activePackages;
    private BigDecimal totalPackageBalance;
    private BigDecimal packageConsumptionRate;

    // 行为/互动
    private Integer bookingCount;
    private Integer noshowCount;
    private BigDecimal noshowRate;
    private Integer reviewCount;
    private BigDecimal avgRating;
    private Integer complaintCount;
    private Integer couponUsedCount;
    private Long pointBalance;

    // 价值度
    private String memberLevel;
    private String rfmSegment;
    private BigDecimal churnRiskScore;
    private BigDecimal ltvScore;

    // 标签
    private String lifecycleLabel;
    private BigDecimal next90dRevenue;
    private Integer next90dActive;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
