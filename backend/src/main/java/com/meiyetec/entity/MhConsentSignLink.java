package com.meiyetec.entity;
import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;
@Data
@TableName("mh_consent_sign_link")
public class MhConsentSignLink {    
@TableId(type = IdType.AUTO)    private Long id;    private Long tenantId;    private Long brandId;    private Long storeId;    private Long memberId;    
private String memberName;    
private String memberPhone;    private Long templateId;    
private String templateCode;    
private String templateVersion;    private Long projectId;    
private String projectName;    
private String token;    
private String signUrl;    
/** 1待签 2已签 3已过期 4已撤销 
*/    private Integer status;    private LocalDateTime expireAt;    private LocalDateTime signedAt;    private Long signatureId;    
private String signatureCode;    private Long operatorId;    
private String operatorName;    
@TableField(fill = FieldFill.INSERT)    private LocalDateTime createTime;    
@TableField(fill = FieldFill.INSERT_UPDATE)    private LocalDateTime updateTime;    @TableLogic    private Integer deleted;}
