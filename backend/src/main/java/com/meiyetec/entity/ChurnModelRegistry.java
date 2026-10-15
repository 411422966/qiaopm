package com.meiyetec.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("churn_model_registry")
public class ChurnModelRegistry {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long tenantId;
    private String modelCode;
    private String modelName;
    private String modelType;
    private String algorithm;

    private Integer featureWindowDays;
    private Integer churnHorizonDays;
    private Integer churnThreshold;
    private Integer trainingSamples;
    private Integer positiveSamples;
    private Integer negativeSamples;

    private BigDecimal trainAuc;
    private BigDecimal trainLogloss;
    private BigDecimal trainAccuracy;
    private BigDecimal trainPrecision;
    private BigDecimal trainRecall;
    private BigDecimal trainF1;
    private BigDecimal valAuc;
    private BigDecimal valLogloss;
    private BigDecimal valAccuracy;
    private BigDecimal valPrecision;
    private BigDecimal valRecall;
    private BigDecimal valF1;

    private String featureImportanceJson;
    private String lrCoefficientsJson;

    private Integer isActive;
    private Integer status;
    private LocalDateTime trainedAt;
    private LocalDateTime deployedAt;
    private String version;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
