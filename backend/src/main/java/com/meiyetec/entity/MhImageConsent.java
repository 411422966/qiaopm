package com.meiyetec.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("mh_image_consent")
public class MhImageConsent {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private Long memberId;
    /** STORAGE/AI_ANALYSIS/ANONYMIZED_PUB/STAFF_INTERNAL */
    private String consentType;
    private String scope;
    /** ELECTRONIC/HANDWRITTEN/WET_INK/OFFLINE */
    private String signMethod;
    private String signatureHash;
    private String signerName;
    private String signerIdNo;
    /** SELF/PARENT/AGENT */
    private String signerRelation;
    private LocalDateTime effectiveAt;
    private LocalDateTime expireAt;
    private String consentDocUrl;
    private Integer revoked;
    private LocalDateTime revokedAt;
    private String revokedReason;
    private Long operatorId;
    private String operatorName;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
    @TableLogic
    private Integer deleted;
}