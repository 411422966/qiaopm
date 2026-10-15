package com.meiyetec.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * POS-008 退款审核规则配置
 */
@Data
@TableName("pos_refund_audit_rule")
public class PosRefundAuditRule {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    /** 审核级别：1店长 2区域经理 3财务总监 */
    private Integer auditLevel;
    private String auditLevelName;
    private BigDecimal minAmount;
    private BigDecimal maxAmount;
    private Integer required;
    private Integer autoApprove;
    private Integer enabled;
    private Long operatorId;
    private String operatorName;
    private String remark;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
