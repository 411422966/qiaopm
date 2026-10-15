package com.meiyetec.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("lbs_dispatch_log")
public class LbsDispatchLog {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private Long storeId;
    private Long orderId;
    private String orderNo;
    /** AUTO / MANUAL / PREVIEW / REASSIGN */
    private String triggerSource;
    /** 候选技师 + 评分 JSON */
    private String candidatesJson;
    private Long chosenTechnicianId;
    private BigDecimal chosenScore;
    private String breakdownJson;
    /** DISPATCHED / REJECTED */
    private String decision;
    private String rejectReason;
    private Integer durationMs;
    private String operatorName;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}