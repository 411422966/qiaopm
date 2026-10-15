package com.meiyetec.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("monitor_alert_event")
public class MonitorAlertEvent {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private Long ruleId;
    private String ruleName;
    private String metricName;
    private Double metricValue;
    private Double threshold;
    private String severity;
    /** FIRING / RESOLVED / SILENCED */
    private String status;
    private LocalDateTime startedAt;
    private LocalDateTime resolvedAt;
    private LocalDateTime acknowledgedAt;
    private String ackedBy;
    private Long incidentId;
    private String message;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}