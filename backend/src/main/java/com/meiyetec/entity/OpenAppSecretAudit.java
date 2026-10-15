package com.meiyetec.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("open_app_secret_audit")
public class OpenAppSecretAudit {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private Long appId;
    private String oldSecretPrefix;
    private String newSecretPrefix;
    private String secretHash;
    /** CREATE / ROTATE / REVOKE */
    private String action;
    private Long operatorId;
    private String operatorName;
    private LocalDateTime createdAt;
}