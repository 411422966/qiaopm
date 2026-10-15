package com.meiyetec.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 会员疗程进度
 * 一个会员购买了一个疗程方案后产生一条；每次到店完成时扣减 remaining_sessions
 */
@Data
@TableName("member_treatment_progress")
public class MemberTreatmentProgress {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private Long memberId;
    private Long planId;
    private Long serviceId;
    private Long packageOrderId;
    private Integer totalSessions;
    private Integer usedSessions;
    private Integer remainingSessions;
    private Integer currentPhaseNo;
    private LocalDateTime startedAt;
    private LocalDateTime lastSessionAt;
    private LocalDateTime expectedFinishAt;
    private LocalDateTime finishedAt;
    /** 1进行中 2已完成 3已停用 */
    private Integer status;
    private Long operatorId;
    private String operatorName;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
    @TableLogic
    private Integer deleted;

    @TableField(exist = false)
    private String memberName;
    @TableField(exist = false)
    private String planName;
    @TableField(exist = false)
    private String serviceName;
    @TableField(exist = false)
    private String currentPhaseName;
    /** 完成百分比 = used / total * 100 */
    @TableField(exist = false)
    private Integer progressPercent;
}
