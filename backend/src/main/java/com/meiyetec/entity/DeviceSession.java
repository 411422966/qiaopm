package com.meiyetec.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("device_session")
public class DeviceSession {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private String deviceId;
    private String platformCode;
    private String sessionToken;
    private String refreshToken;
    private LocalDateTime expiresAt;
    private String ip;
    private String userAgent;
    private Integer active;
    private LocalDateTime startedAt;
    private LocalDateTime endedAt;
}