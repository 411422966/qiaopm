package com.meiyetec.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("no_show_model")
public class NoShowModel {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private String modelCode;
    private String modelName;
    private String algorithm;
    private String version;
    private BigDecimal accuracy;
    private BigDecimal precisionScore;
    private BigDecimal recallScore;
    private BigDecimal f1Score;
    private BigDecimal auc;
    private String featureJson;
    private LocalDateTime trainedAt;
    private Integer sampleCount;
    private Integer isActive;
    private String description;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}