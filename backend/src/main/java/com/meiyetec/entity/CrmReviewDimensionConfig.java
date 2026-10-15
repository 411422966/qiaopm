package com.meiyetec.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("crm_review_dimension_config")
public class CrmReviewDimensionConfig {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private String dimensionCode;
    private String dimensionName;
    private BigDecimal weight;
    private BigDecimal targetScore;
    private Integer enabled;
    private Integer sortOrder;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}