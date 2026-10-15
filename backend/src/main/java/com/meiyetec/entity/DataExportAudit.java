package com.meiyetec.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("data_export_audit")
public class DataExportAudit {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private Long operatorId;
    private String operatorName;
    /** MEMBER / ORDER / REPORT / FINANCIAL */
    private String exportType;
    private String bizFilter;
    private Integer rowCount;
    private String fileUrl;
    private String fileHash;
    private String encryptKeyId;
    private String ipAddress;
    private String purpose;
    private Integer approved;
    private String approvedBy;
    private LocalDateTime approvedAt;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}