package com.meiyetec.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("sla_uptime_record")
public class SlaUptimeRecord {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private String serviceCode;
    private LocalDate recordDate;
    private Integer totalMinutes;
    private Integer downMinutes;
    private Integer uptimeMinutes;
    private BigDecimal availability;
    private Integer incidentCount;
    private Integer p95ResponseMs;
    private Integer p99ResponseMs;
    private Integer errorCount;
    private Long totalRequests;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}