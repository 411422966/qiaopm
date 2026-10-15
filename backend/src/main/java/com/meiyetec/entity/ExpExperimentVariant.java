package com.meiyetec.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * R3.0-E P2：实验变体（A/B/C 等）+ 流量权重
 */
@Data
@TableName("exp_experiment_variant")
public class ExpExperimentVariant {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private Long experimentId;
    private String variantCode;
    private String variantName;
    private Integer trafficWeight;
    private String payload;
    private Integer isControl;
    private Long exposureCount;
    private Long conversionCount;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
    @TableLogic
    private Integer deleted;
}