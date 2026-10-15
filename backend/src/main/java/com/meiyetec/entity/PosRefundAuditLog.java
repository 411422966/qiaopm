package com.meiyetec.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * POS-008 退款审核流水 / 时间线
 * <p>记录每一次审核动作：SUBMIT / AUTO_APPROVE / L1_APPROVE / L2_APPROVE / L3_APPROVE / REJECT / RESUBMIT / CANCEL / PROCESS / SUCCESS / FAILED
 */
@Data
@TableName("pos_refund_audit_log")
public class PosRefundAuditLog {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private Long refundId;
    private String refundNo;
    /** 审核级别：0无需 / 1店长 / 2区域经理 / 3财务总监 */
    private Integer auditLevel;
    private String auditLevelName;
    private String action;
    private Long actorId;
    private String actorName;
    private String actorRole;
    private String beforeStatus;
    private String afterStatus;
    private String remark;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
