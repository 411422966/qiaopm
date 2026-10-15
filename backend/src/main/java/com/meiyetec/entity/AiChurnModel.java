package com.meiyetec.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("ai_churn_model")
public class AiChurnModel {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private String modelCode;
    private String modelName;
    private String version;
    /** WEIGHTED_RULE / LOGISTIC / XGBOOST */
    private String algorithm;
    private BigDecimal thresholdHigh;
    private BigDecimal thresholdMed;
    private Integer isActive;
    private Integer enabled;
    private Integer status;
    private String description;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
    @TableLogic
    private Integer deleted;
}