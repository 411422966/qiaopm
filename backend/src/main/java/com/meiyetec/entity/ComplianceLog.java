package com.meiyetec.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("compliance_log")
public class ComplianceLog {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    /** LOGIN / DATA_ACCESS / DATA_EXPORT / CONFIG_CHANGE / PERMISSION_GRANT */
    private String logType;
    private Long operatorId;
    private String operatorName;
    private String targetType;
    private String targetId;
    private String action;
    private String detail;
    private String ipAddress;
    private String deviceId;
    private String geoLocation;
    /** INFO / WARN / CRITICAL */
    private String severity;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}