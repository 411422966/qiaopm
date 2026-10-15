package com.meiyetec.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("member_credit_log")
public class MemberCreditLog {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private Long memberId;
    /** NO_SHOW/ON_TIME/CANCEL_LATE/MANUAL */
    private String changeType;
    private Integer changeValue;
    private Integer creditAfter;
    private Long bookingId;
    private Long orderId;
    private java.math.BigDecimal feePenalty;
    private LocalDateTime lockedUntil;
    private Long operatorId;
    private String operatorName;
    private String remark;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    @TableLogic
    private Integer deleted;
}
