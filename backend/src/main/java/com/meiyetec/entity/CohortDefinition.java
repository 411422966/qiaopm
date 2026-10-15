package com.meiyetec.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("cohort_definition")
public class CohortDefinition {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private String cohortCode;
    private String cohortName;
    private String cohortType;
    private String metric;
    private String targetDim;
    private String description;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}