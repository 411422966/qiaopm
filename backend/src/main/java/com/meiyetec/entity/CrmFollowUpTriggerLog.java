package com.meiyetec.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * R3.0-H P0.5：回访计划触发日志
 * 记录每次手动触发或调度触发生成的回访任务数，用于 stats / week-trend 聚合。
 */
@Data
@TableName("crm_follow_up_trigger_log")
public class CrmFollowUpTriggerLog {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private Long planId;
    private String planCode;
    private Integer generatedCount;
    /** MANUAL / SCHEDULED */
    private String triggerSource;
    private LocalDateTime triggerTime;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
