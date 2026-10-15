package com.meiyetec.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("ai_tag_model")
public class AiTagModel {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private Long brandId;
    private String modelCode;
    private String modelName;
    /** RFM/RULE/ML/AI_VISION */
    private String modelType;
    private String ruleJson;
    private String scheduleCron;
    private Integer enabled;
    private Integer status;
    private String description;
    private BigDecimal auc;
    private BigDecimal recall;
    private BigDecimal precisionScore;
    private Long operatorId;
    private String operatorName;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
    @TableLogic
    private Integer deleted;
}