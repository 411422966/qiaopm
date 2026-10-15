package com.meiyetec.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * R3.0-G P3-3：开放平台应用（合作伙伴注册）
 */
@Data
@TableName("open_app")
public class OpenApp {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private String appCode;
    private String appName;
    private String appSecret;
    /** PARTNER / INTERNAL */
    private String appType;
    /** ACTIVE/SUSPENDED/REVOKED */
    private String status;
    private String contactEmail;
    private String contactName;
    private Integer rateLimit;
    private String allowedIps;
    private String scopes;
    private LocalDateTime approvedAt;
    private LocalDateTime expiresAt;
    private Long operatorId;
    private String operatorName;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
    @TableLogic
    private Integer deleted;
}