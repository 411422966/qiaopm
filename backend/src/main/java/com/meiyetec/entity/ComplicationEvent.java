package com.meiyetec.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("complication_event")
public class ComplicationEvent {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private String eventNo;
    private Long sopId;
    private String sopCode;
    private Long memberId;
    private Long orderId;
    private Long serviceId;
    private String severity;
    private String symptoms;
    private LocalDateTime triggerTime;
    private LocalDateTime responseTime;
    private LocalDateTime resolvedTime;
    private Long handlerId;
    private String handlerName;
    private String status;
    private String executionLog;
    private String outcome;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}