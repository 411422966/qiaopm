package com.meiyetec.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("risk_event")
public class RiskEvent {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private String eventNo;
    private Long ruleId;
    private String ruleCode;
    private String ruleLevel;
    private String bizType;
    private Long bizId;
    private String bizNo;
    private Long memberId;
    private BigDecimal amount;
    private Integer riskScore;
    private String action;
    private String description;
    private Integer handled;
    private String handleStatus;
    private Long handlerId;
    private String handleNotes;
    private LocalDateTime handledAt;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}