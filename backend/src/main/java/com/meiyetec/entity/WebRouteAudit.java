package com.meiyetec.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("web_route_audit")
public class WebRouteAudit {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private Long userId;
    private String deviceId;
    private String platformCode;
    private String routePath;
    private String routeName;
    private Integer durationMs;
    private Integer apiCalls;
    private String ip;
    private String userAgent;
    private String errorMessage;
    private LocalDateTime visitedAt;
}