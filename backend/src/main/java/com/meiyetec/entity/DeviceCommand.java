package com.meiyetec.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("device_command")
public class DeviceCommand {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private String deviceId;
    /** REFRESH / LOCK / LOGOUT / PUSH / WIPE */
    private String commandType;
    /** JSON */
    private String payload;
    /** PENDING / DELIVERED / ACK / FAILED */
    private String status;
    private Integer priority;
    private LocalDateTime expiresAt;
    private LocalDateTime createdAt;
    private LocalDateTime deliveredAt;
    private LocalDateTime ackedAt;
    private String errorMessage;
}