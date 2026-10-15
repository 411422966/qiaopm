package com.meiyetec.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("iot_event_dest")
public class IotEvent {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private Long deviceId;
    private String deviceCode;
    /** TELEMETRY / ALERT / STATUS / ERROR */
    private String eventType;
    private String eventPayload;
    /** INFO / WARN / ERROR / CRITICAL */
    private String severity;
    private LocalDateTime occurredAt;
    /** 0=未处理 */
    private Integer processed;
    private LocalDateTime processedAt;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime receivedAt;
}