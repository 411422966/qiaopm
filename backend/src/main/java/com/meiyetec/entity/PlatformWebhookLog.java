package com.meiyetec.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * R3.0-G P3-2：第三方 webhook 日志（幂等 + 审计）
 */
@Data
@TableName("platform_webhook_log")
public class PlatformWebhookLog {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String platform;
    private String eventType;
    private String payloadHash;
    private String rawBody;
    private Integer processed;
    private String processedMsg;
    private LocalDateTime receivedAt;
}