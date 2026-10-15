package com.meiyetec.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.sql.Time;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("mkt_dynamic_pricing_rule")
public class MktDynamicPricingRule {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private Long brandId;
    private String ruleCode;
    private String ruleName;
    /** TECHNICIAN/TIME_SLOT/PROJECT/MEMBER_LEVEL/STORE */
    private String ruleType;
    private String targetType;
    private Long targetId;
    private String targetCode;
    private String targetName;
    /** PERCENT/FIXED/MULTIPLIER */
    private String pricingMode;
    private BigDecimal pricingValue;
    private BigDecimal minAmount;
    private BigDecimal maxAmount;
    private Time timeStart;
    private Time timeEnd;
    private String dayOfWeek;
    private Integer priority;
    private Integer enabled;
    private LocalDate validFrom;
    private LocalDate validTo;
    private String description;
    private Long operatorId;
    private String operatorName;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
    @TableLogic
    private Integer deleted;
}