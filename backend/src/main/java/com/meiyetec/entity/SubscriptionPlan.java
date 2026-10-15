package com.meiyetec.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("subscription_plan")
public class SubscriptionPlan {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private String planCode;
    private String planName;
    private String planType;
    private BigDecimal price;
    private Integer billingCycleDays;
    private Integer trialDays;
    private Integer serviceCount;
    private String includedServices;
    private BigDecimal discountRate;
    private Integer giftPoints;
    private String extraBenefits;
    private String description;
    private String coverImage;
    private Integer totalSubscribers;
    private Integer activeSubscribers;
    private String status;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}