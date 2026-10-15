package com.meiyetec.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("platform_webhook_event")
public class PlatformWebhookEvent {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private String name;
    private String description;
    private Integer enabled;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}