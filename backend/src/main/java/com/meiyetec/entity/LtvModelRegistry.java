package com.meiyetec.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("ltv_model_registry")
public class LtvModelRegistry {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long tenantId;
    private String modelCode;
    private String modelName;
    private String modelType;
    private String algorithm;

    private Integer featureWindowDays;
    private Integer labelWindowDays;
    private Integer trainingSamples;
    private Integer featureCount;

    private BigDecimal trainMae;
    private BigDecimal trainRmse;
    private BigDecimal trainMape;
    private BigDecimal trainR2;
    private BigDecimal valMae;
    private BigDecimal valRmse;
    private BigDecimal valMape;
    private BigDecimal valR2;
    private BigDecimal valAuc;

    private String featureImportanceJson;

    private Integer isActive;
    private Integer status;
    private LocalDateTime trainedAt;
    private LocalDateTime deployedAt;
    private String version;
    private String modelFormula;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
