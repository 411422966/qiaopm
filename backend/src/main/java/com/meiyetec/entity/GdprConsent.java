package com.meiyetec.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("gdpr_consent")
public class GdprConsent {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private Long memberId;
    private String consentType;
    private Integer granted;
    private String version;
    private String ipAddress;
    private String userAgent;
    private String channel;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}