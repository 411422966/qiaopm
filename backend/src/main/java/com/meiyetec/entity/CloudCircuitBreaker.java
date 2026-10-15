package com.meiyetec.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("cloud_circuit_breaker")
public class CloudCircuitBreaker {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private String breakerName;
    private String serviceCode;
    /** CLOSED / OPEN / HALF_OPEN */
    private String state;
    private Integer failureCount;
    private Integer successCount;
    private Integer failureThreshold;
    private Integer recoveryWindowSec;
    private LocalDateTime openedAt;
    private LocalDateTime lastFailureAt;
    private LocalDateTime lastStateChange;
    private String errorMessage;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}