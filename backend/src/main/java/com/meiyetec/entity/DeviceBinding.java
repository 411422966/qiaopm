package com.meiyetec.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("device_binding")
public class DeviceBinding {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private Long userId;
    private String deviceId;
    private String platformCode;
    private String deviceName;
    private String deviceModel;
    private String osVersion;
    private String appVersion;
    private String pushToken;
    /** ACTIVE / OFFLINE / REVOKED */
    private String status;
    private LocalDateTime lastSeenAt;
    private String ip;
    private LocalDateTime bindAt;
}