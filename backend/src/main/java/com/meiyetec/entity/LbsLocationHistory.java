package com.meiyetec.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("lbs_location_history")
public class LbsLocationHistory {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private Long technicianId;
    private BigDecimal lat;
    private BigDecimal lng;
    private BigDecimal accuracyM;
    private BigDecimal speedKmh;
    private Integer batteryPct;
    /** APP / MANUAL / SIMULATE */
    private String source;
    private LocalDateTime reportedAt;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}