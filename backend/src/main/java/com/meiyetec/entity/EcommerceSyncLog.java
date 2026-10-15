package com.meiyetec.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("ecommerce_sync_log")
public class EcommerceSyncLog {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private String platform;
    private String syncType;
    private Integer successCount;
    private Integer failCount;
    private Integer durationMs;
    private String status;
    private String errorMessage;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}