package com.meiyetec.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("monitor_metric")
public class MonitorMetric {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private String metricName;
    /** GAUGE / COUNTER / HISTOGRAM / SUMMARY */
    private String metricType;
    /** SERVICE / INSTANCE / DB / CACHE / API */
    private String targetType;
    private String targetCode;
    private Double value;
    private String unit;
    private String tags;
    private LocalDateTime sampledAt;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}