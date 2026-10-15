package com.meiyetec.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("mkt_points_rule")
public class MktPointsRule {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private String ruleName;
    private BigDecimal consumeRate;
    private BigDecimal rechargeMultiplier;
    private BigDecimal promotionMultiplier;
    private BigDecimal pointsToCash;
    private BigDecimal maxUsePercent;
    private Integer enabled;
    private Integer priority;
    private LocalDate effectiveDate;
    private LocalDate expiryDate;
    private Long operatorId;
    private String operatorName;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
    @TableLogic
    private Integer deleted;
}
