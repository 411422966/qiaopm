package com.meiyetec.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 评价驱动的服务改进任务（闭环）
 */
@Data
@TableName("crm_service_improvement")
public class CrmServiceImprovement {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private Long brandId;
    private Long storeId;
    private String improvementNo;
    private String title;
    /** REVIEW / COMPLAINT / VOICE_OF_CUSTOMER / INTERNAL */
    private String sourceType;
    private Long sourceId;
    private String sourceNo;
    private String dimensionCode;
    private BigDecimal currentScore;
    private BigDecimal targetScore;
    private BigDecimal gapScore;
    private String problem;
    private String rootCause;
    private String actionPlan;
    private String expectedEffect;
    private Long ownerId;
    private String ownerName;
    /** 1=待评估 2=待审批 3=执行中 4=已完成 5=已取消 */
    private Integer status;
    /** 1=低 2=中 3=高 4=紧急 */
    private Integer priority;
    private LocalDateTime startedAt;
    private LocalDateTime completedAt;
    private LocalDateTime deadline;
    private BigDecimal postScore;
    private String effectNote;
    private String operatorName;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
    @TableLogic
    private Integer deleted;
}