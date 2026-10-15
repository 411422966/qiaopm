package com.meiyetec.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;

@Data
@TableName("complaint_sla_policy")
public class ComplaintSlaPolicy {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    /** P1 / P2 / P3 / P4 */
    private String priority;
    private Integer responseMinutes;
    private Integer resolveMinutes;
    private String escalateTo;
    private String notifyChannels;
    private BigDecimal compensationLimit;
}