package com.meiyetec.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;
import java.math.BigDecimal;

/**
 * R3.0-E P2：AB 实验主表
 */
@Data
@TableName("exp_experiment")
public class ExpExperiment {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private Long brandId;
    private String expCode;
    private String expName;
    /** UI/PRICE/COPY/FUNCTION/FLOW */
    private String expType;
    private String description;
    /** DRAFT/RUNNING/PAUSED/FINISHED */
    private String status;
    private LocalDateTime startedAt;
    private LocalDateTime endedAt;
    private String targetMetric;
    private Integer sampleSize;
    private BigDecimal confidenceLevel;
    private Long operatorId;
    private String operatorName;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
    @TableLogic
    private Integer deleted;
}