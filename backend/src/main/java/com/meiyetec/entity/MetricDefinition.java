package com.meiyetec.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("metric_definition")
public class MetricDefinition {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private String metricCode;
    private String metricName;
    private String metricCategory;
    private String description;
    private String unit;
    private String sqlTemplate;
    private String sourceTable;
    private String defaultDimensions;
    private String aggregation;
    private BigDecimal targetValue;
    private BigDecimal warningValue;
    private Integer higherIsBetter;
    private String owner;
    private String status;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}