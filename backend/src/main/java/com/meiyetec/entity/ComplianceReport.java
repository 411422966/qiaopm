package com.meiyetec.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("compliance_report")
public class ComplianceReport {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private String reportKey;
    /** GDPR / PIPL / SOX / HIPAA / CUSTOM */
    private String reportType;
    private LocalDateTime periodStart;
    private LocalDateTime periodEnd;
    private Integer totalLogs;
    private Integer criticalCount;
    private Integer warnCount;
    private Integer infoCount;
    private String summary;
    private String metrics;
    /** PENDING / APPROVED / SUBMITTED */
    private String status;
    private String operatorName;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}