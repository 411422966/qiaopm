package com.meiyetec.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * R3.0-H P1.4：会员疗程步骤执行日志
 */
@Data
@TableName("member_treatment_step_log")
public class MemberTreatmentStepLog {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private Long progressId;
    private Long sessionId;
    private Long stepId;
    private Long phaseId;
    private Long planId;
    private Long memberId;
    private String stepName;
    private String stepGroup;
    private Integer durationMinutes;
    private Long productId;
    private BigDecimal productQty;
    private Long operatorId;
    private String operatorName;
    private String remark;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;
    /** 1=执行中 / 2=已完成 / 3=跳过 */
    private Integer status;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}