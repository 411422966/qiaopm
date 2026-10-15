package com.meiyetec.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * R3.0-G P3-4：设备事件流（HEARTBEAT/UNLOCK/...）
 */
@Data
@TableName("iot_device_event")
public class IotDeviceEvent {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private Long deviceId;
    private String deviceSn;
    private String eventType;
    private String eventData;
    /** INFO/WARN/ERROR */
    private String severity;
    private Integer handled;
    private LocalDateTime handledAt;
    private String handledRemark;
    private LocalDateTime occurredAt;
}