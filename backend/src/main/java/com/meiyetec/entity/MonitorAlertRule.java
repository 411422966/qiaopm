package com.meiyetec.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("monitor_alert_rule")
public class MonitorAlertRule {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private String ruleName;
    private String metricName;
    /** > / >= / < / <= / == / != */
    private String comparator;
    private Double threshold;
    /** INFO / WARN / ERROR / CRITICAL */
    private String severity;
    /** 持续时间触发 */
    private Integer durationSec;
    private String channels;
    private String recipients;
    private Integer silenceMinutes;
    private Integer enabled;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}