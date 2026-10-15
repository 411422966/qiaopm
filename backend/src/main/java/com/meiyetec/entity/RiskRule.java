package com.meiyetec.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("risk_rule")
public class RiskRule {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private String ruleCode;
    private String ruleName;
    private String ruleType;
    private String ruleLevel;
    private String conditionExpr;
    private BigDecimal threshold;
    private String action;
    private String notifyChannel;
    private Integer enabled;
    private Integer hitCount;
    private LocalDateTime lastHitAt;
    private String description;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}