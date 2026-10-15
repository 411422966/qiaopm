package com.meiyetec.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("marketing_touch_task")
public class MarketingTouchTask {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private Long brandId;
    private Long storeId;
    private String taskNo;
    /** JOURNEY/MANUAL/SCHEDULE/TRIGGER */
    private String sourceType;
    private Long journeyId;
    private Long journeyNodeId;
    private String campaignName;
    private String channelType;
    private Long templateId;
    private String templateSnapshot;
    private String triggerEvent;
    private Integer triggerOffsetMinutes;
    private String audienceFilter;
    private LocalDateTime scheduledAt;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;
    /** 1待执行 2执行中 3已完成 4已暂停 5已失败 6已取消 */
    private Integer status;
    private Integer targetCount;
    private Integer successCount;
    private Integer failCount;
    private Integer readCount;
    private Integer convertCount;
    private Integer maxReachPerDay;
    private String remark;
    private Long operatorId;
    private String operatorName;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
    @TableLogic
    private Integer deleted;
}
