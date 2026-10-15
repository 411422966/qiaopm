package com.meiyetec.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 疗程方案：服务项目的"组合套餐"，如"美白亮肤 8 次套餐"
 */
@Data
@TableName("biz_treatment_plan")
public class BizTreatmentPlan {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private Long storeId;
    private String planCode;
    private String planName;
    private Long serviceId;
    private Long categoryId;
    private Integer totalSessions;
    private Integer cycleDays;
    private BigDecimal planPrice;
    private BigDecimal memberPrice;
    private String description;
    private String sop;
    private Integer status;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
    @TableLogic
    private Integer deleted;

    @TableField(exist = false)
    private String serviceName;
    @TableField(exist = false)
    private String categoryName;
    @TableField(exist = false)
    private java.util.List<BizTreatmentPhase> phases;
}
