package com.meiyetec.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("churn_training_run_log")
public class ChurnTrainingRunLog {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long tenantId;
    private Long modelId;
    private String runType;
    private String step;
    private LocalDateTime startAt;
    private LocalDateTime endAt;
    private Integer durationMs;
    private Integer samples;
    private String metric;
    private BigDecimal metricValue;
    private String message;
    private String error;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
