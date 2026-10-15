package com.meiyetec.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("platform_webhook_delivery")
public class PlatformWebhookDelivery {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private Long subscriptionId;
    private String eventName;
    private String payload;
    private String payloadHash;
    private String targetUrl;
    private String signingSecret;
    private String signature;
    private Integer attempt;
    /** PENDING / SUCCESS / FAILED / DEAD_LETTER */
    private String status;
    private Integer httpStatus;
    private String responseBody;
    private Integer durationMs;
    private LocalDateTime nextRetryAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}