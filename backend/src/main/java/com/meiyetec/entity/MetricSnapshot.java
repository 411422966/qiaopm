package com.meiyetec.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("metric_snapshot")
public class MetricSnapshot {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private Long metricId;
    private String metricCode;
    private LocalDate snapshotDate;
    private BigDecimal value;
    private Long dimId;
    private Long storeId;
    private String period;
    private Integer calcDurationMs;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}