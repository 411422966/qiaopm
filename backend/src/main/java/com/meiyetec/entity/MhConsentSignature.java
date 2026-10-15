package com.meiyetec.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("mh_consent_signature")
public class MhConsentSignature {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private Long brandId;
    private Long storeId;
    private Long memberId;
    private Long templateId;
    private String templateCode;
    private String templateVersion;
    private Long projectId;
    private String projectName;
    private Long orderId;
    private String orderNo;
    private String signatureId;
    private String signDocUrl;
    private String signDocHash;
    private String signerName;
    private String signerIdNo;
    /** SELF/PARENT/AGENT */
    private String signerRelation;
    private Long witnessId;
    private String witnessName;
    private String witnessLicenseNo;
    private String checkedItems;
    private String remark;
    private LocalDateTime effectiveAt;
    /** 1已签署 2已撤回 3已过期 */
    private Integer status;
    private LocalDateTime revokedAt;
    private String revokedReason;
    private String signIp;
    private String signDevice;
    private Long operatorId;
    private String operatorName;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
    @TableLogic
    private Integer deleted;
}