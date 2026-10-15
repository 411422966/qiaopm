package com.meiyetec.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("iot_device")
public class IotDevice {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private Long storeId;
    private String deviceCode;
    private String deviceSn;
    private String deviceName;
    private String deviceType;
    private String protocol;
    private String manufacturer;
    private String model;
    private String firmwareVersion;
    private String firmware;
    private String macAddress;
    private String ipAddress;
    private String vendor;
    private String mqttClientId;
    private String mqttTopic;
    private String authToken;
    private String lastTelemetry;
    private LocalDateTime lastOnlineAt;
    private LocalDateTime lastHeartbeat;
    private String onlineStatus;
    private Long bindMemberId;
    private String status;
    private Integer enabled;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}