package com.meiyetec.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * R3.0-G P3-3：开放平台令牌（OAuth2 client_credentials）
 */
@Data
@TableName("open_access_token")
public class OpenAccessToken {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long appId;
    private String accessToken;
    private String refreshToken;
    private LocalDateTime expiresAt;
    private LocalDateTime refreshExpiresAt;
    private String scopes;
    private String clientIp;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}