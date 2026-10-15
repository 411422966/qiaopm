package com.meiyetec.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;
import java.math.BigDecimal;

/**
 * R3.0-E P2：实验分配记录（每个 subject 分配到哪个变体、是否转化）
 */
@Data
@TableName("exp_assignment")
public class ExpAssignment {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private Long experimentId;
    private Long variantId;
    private String subjectId;
    private LocalDateTime exposureAt;
    private Integer converted;
    private LocalDateTime convertedAt;
    private BigDecimal metricValue;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}