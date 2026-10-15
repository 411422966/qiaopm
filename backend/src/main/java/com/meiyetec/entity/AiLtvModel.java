package com.meiyetec.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("ai_ltv_model")
public class AiLtvModel {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private Long brandId;
    private String modelCode;
    private String modelName;
    /** BG/NBD/HISTORICAL/ML */
    private String modelType;
    private LocalDate trainStart;
    private LocalDate trainEnd;
    private BigDecimal auc;
    private BigDecimal recall;
    private BigDecimal precisionScore;
    private String paramsJson;
    /** 1训练中 2已部署 3停用 */
    private Integer status;
    private Integer isActive;
    private Integer enabled;
    private Long operatorId;
    private String operatorName;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
    @TableLogic
    private Integer deleted;
}