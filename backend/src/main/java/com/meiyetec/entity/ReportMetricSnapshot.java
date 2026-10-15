package com.meiyetec.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("report_metric_snapshot")
public class ReportMetricSnapshot {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private Long storeId;
    private LocalDate snapshotDate;
    private String metricKey;
    private String metricName;
    private BigDecimal metricValue;
    private String extra;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
