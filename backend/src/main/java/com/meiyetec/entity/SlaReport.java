package com.meiyetec.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("sla_report")
public class SlaReport {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private String reportKey;
    /** YYYY-MM */
    private String periodMonth;
    private String serviceCode;
    private BigDecimal availabilityActual;
    private BigDecimal availabilityTarget;
    private Integer achieved;
    private Integer downtimeSeconds;
    private Integer downtimeBudgetSec;
    private Integer incidentCount;
    private Integer p95AvgMs;
    private Integer p99AvgMs;
    private BigDecimal errorRate;
    private String summary;
    /** GENERATED / SUBMITTED / ACKNOWLEDGED */
    private String status;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}