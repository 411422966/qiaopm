package com.meiyetec.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("cohort_result")
public class CohortResult {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private Long cohortId;
    private String cohortCode;
    private String cohortPeriod;
    private Integer size;
    private Integer periodIndex;
    private BigDecimal value;
    private String valueType;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}