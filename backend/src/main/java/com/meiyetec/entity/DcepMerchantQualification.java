package com.meiyetec.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("dcep_merchant_qualification")
public class DcepMerchantQualification {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long configId;

    // 法人
    private String legalPerson;
    private String legalIdNo;
    private String legalIdFront;
    private String legalIdBack;

    // 营业执照
    private String businessLicenseNo;
    private String businessLicenseImg;
    private String orgCode;
    private String taxNo;

    // 银行
    private String bankAccountName;
    private String bankAccountNo;
    private String bankName;
    private String bankBranch;

    // 联系方式
    private String contactPerson;
    private String contactPhone;
    private String contactEmail;
    private String address;
    private String remark;

    /** 0=待提交 1=审核中 2=已通过 3=已驳回 */
    private Integer auditStatus;
    private String auditRemark;
    private LocalDateTime auditAt;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    /** 关联附件，运行时填充，不入库 */
    @TableField(exist = false)
    private java.util.List<DcepQualificationAttachment> attachments;
}