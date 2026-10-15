package com.meiyetec.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("mkt_pricing_log")
public class MktPricingLog {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private Long projectId;
    private String projectName;
    private Long technicianId;
    private String technicianName;
    private Long memberId;
    private Long memberLevelId;
    private Long storeId;
    private LocalDateTime pricingTime;
    private BigDecimal baseAmount;
    private Long hitRuleId;
    private String hitRuleCode;
    private BigDecimal finalAmount;
    private BigDecimal discountAmount;
    private String rulesJson;
    private Integer checkoutVisible;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}