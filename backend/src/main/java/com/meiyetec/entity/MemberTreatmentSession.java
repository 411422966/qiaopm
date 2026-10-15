package com.meiyetec.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 会员每次到店做服务的记录
 */
@Data
@TableName("member_treatment_session")
public class MemberTreatmentSession {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private Long progressId;
    private Long memberId;
    private Long planId;
    private Integer phaseNo;
    private Integer sessionNo;
    private Long orderId;
    private Long employeeId;
    private String employeeName;
    private LocalDateTime sessionAt;
    private String remark;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    @TableLogic
    private Integer deleted;
}
