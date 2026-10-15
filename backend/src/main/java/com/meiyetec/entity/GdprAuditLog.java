package com.meiyetec.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("gdpr_audit_log")
public class GdprAuditLog {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private Long actorId;
    private String actorName;
    private String action;
    private String targetType;
    private Long targetId;
    private Integer piiData;
    private String details;
    private String ipAddress;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}