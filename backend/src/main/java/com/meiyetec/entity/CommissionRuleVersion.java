package com.meiyetec.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("commission_rule_version")
public class CommissionRuleVersion {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private Long storeId;
    private Long ruleId;
    private Integer versionNo;
    private String ruleName;
    private String businessType;
    private Long targetId;
    private String calculationMethod;
    private java.math.BigDecimal rate;
    private java.math.BigDecimal fixedAmount;
    private Integer priority;
    private LocalDate effectiveDate;
    private LocalDate expiryDate;
    private String snapshotReason;
    private Long operatorId;
    private String operatorName;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    @TableLogic
    private Integer deleted;
}
