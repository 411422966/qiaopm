package com.meiyetec.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("service_route_rule")
public class ServiceRouteRule {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String routePattern;
    private String targetService;
    private String targetPath;
    private Integer stripPrefix;
    private Integer retryCount;
    private Integer timeoutMs;
    private Integer rateLimitQps;
    private Integer authRequired;
    private Integer priority;
    private Integer enabled;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}