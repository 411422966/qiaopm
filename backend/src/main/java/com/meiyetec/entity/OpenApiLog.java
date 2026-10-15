package com.meiyetec.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * R3.0-G P3-3：开放平台 API 调用日志（审计/限流统计）
 */
@Data
@TableName("open_api_log")
public class OpenApiLog {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private Long appId;
    private String appCode;
    private String method;
    private String path;
    private String requestId;
    private String clientIp;
    private Integer httpStatus;
    private Integer durationMs;
    private String errorCode;
    private String errorMessage;
    private String requestBody;
    private String responseBody;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}