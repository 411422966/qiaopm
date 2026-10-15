package com.meiyetec.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("platform_webhook_subscription")
public class PlatformWebhookSubscription {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private String name;
    private String targetUrl;
    private String signingSecret;
    /** 事件列表（逗号分隔），*=全部 */
    private String eventsCsv;
    private Integer enabled;
    /** EXP_BACKOFF / FIXED_DELAY */
    private String retryStrategy;
    private Integer maxAttempts;
    private Integer timeoutSeconds;
    private LocalDateTime lastTriggeredAt;
    private Long successCount;
    private Long failureCount;
    private String description;
    private String operatorName;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
    @TableLogic
    private Integer deleted;
}