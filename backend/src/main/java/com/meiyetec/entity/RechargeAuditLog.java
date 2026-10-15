package com.meiyetec.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * M05 充值审核操作日志
 */
@Data
@TableName("crm_recharge_audit_log")
public class RechargeAuditLog {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private Long auditId;
    /** 审核单号 */
    private String auditNo;
    /** 操作类型：1提交申请 2审核通过 3审核拒绝 4取消 5发放成功 */
    private Integer action;
    /** 操作人 id */
    private Long operatorId;
    private String operatorName;
    private String remark;
    private String beforeStatus;
    private String afterStatus;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    @TableLogic
    private Integer deleted;
}
