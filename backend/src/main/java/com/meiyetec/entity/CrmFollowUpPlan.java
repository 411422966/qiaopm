package com.meiyetec.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("crm_follow_up_plan")
public class CrmFollowUpPlan {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private Long brandId;
    private Long storeId;
    private String storeIds;
    private String planCode;
    private String planName;
    /** POST_TREATMENT/BIRTHDAY/EXPIRE/CUSTOM */
    private String planType;
    private Integer triggerOffsetDays;
    private String triggerEvent;
    private String subjectTemplate;
    private String contentTemplate;
    private String scriptTemplate;
    private Integer priority;
    private Integer slaMinutes;
    private Long defaultOwnerId;
    private String defaultOwnerName;
    private Integer enabled;
    private Long operatorId;
    private String operatorName;
    private LocalDateTime lastRunAt;
    private Integer lastRunCount;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
    @TableField(fill = FieldFill.INSERT)
    private String createBy;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private String updateBy;
    @TableLogic
    private Integer deleted;
}
