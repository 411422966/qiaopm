package com.meiyetec.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("mh_consent_template")
public class MhConsentTemplate {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private Long brandId;
    private String templateCode;
    private String templateName;
    /** GENERAL/SURGERY/INJECTION/LASER/SKINCARE/PHOTO */
    private String templateType;
    private Long projectId;
    private String projectName;
    private String contentMd;
    private String requiredItems;
    private String risksMd;
    private String legalBasis;
    private String version;
    /** 1待审核 2已审核 3驳回 */
    private Integer auditStatus;
    private Integer enabled;
    private Long operatorId;
    private String operatorName;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
    @TableLogic
    private Integer deleted;
}