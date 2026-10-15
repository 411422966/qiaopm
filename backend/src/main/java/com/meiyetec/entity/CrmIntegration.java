package com.meiyetec.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * CRM 集成配置
 *
 * <p>EXT-15 扩展：sync_token / sync_schedule / OAuth2 JWT 凭据（username / privateKey / loginUrl）。
 *
 * <p>平台支持 SALESFORCE / HUBSPOT / MOCK。
 */
@Data
@TableName("crm_integration")
public class CrmIntegration {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    /** SALESFORCE / HUBSPOT / MOCK */
    private String platform;
    private String appId;
    private String clientId;
    private String clientSecret;
    private String accessToken;
    private String refreshToken;
    private LocalDateTime tokenExpireAt;
    private String apiBaseUrl;
    private String portalId;
    /** PUSH / PULL / BIDIRECTIONAL */
    private String syncDirection;
    private Integer syncMembers;
    private Integer syncOrders;
    private Integer syncContacts;
    private Integer syncDeals;
    private Integer autoSync;
    private LocalDateTime lastSyncAt;
    /** ACTIVE / DISABLED / ERROR */
    private String status;
    private String errorMessage;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    // ============= EXT-15：sync_token / schedule / OAuth2 JWT 凭据 =============

    /** 平台 sync token（Salesforce 用于增量查询游标） */
    private String syncToken;
    /** cron 表达式（默认 "0 *&#x2F;30 * * * *" — 每 30 分钟） */
    private String syncSchedule;
    /** 排程是否启用（0=否 1=是） */
    private Integer syncEnabled;
    /** 上次成功增量同步时间戳 */
    private LocalDateTime lastIncrementalSyncAt;
    /** OAuth2 JWT 签名私钥（PEM，建议上层加密存储） */
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private String privateKey;
    /** 平台账号（Salesforce username for JWT flow） */
    private String username;
    /** 登录端点（Salesforce: login.salesforce.com 或 test.salesforce.com） */
    private String loginUrl;
    /** 字段映射 JSON：localField → externalField */
    private String objectMappings;
    /** 增量同步窗口（分钟） */
    private Integer syncWindowMinutes;

    /**
     * 二次确认密码（不入库，仅供控制器校验）
     */
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private transient String confirmPassword;
}
