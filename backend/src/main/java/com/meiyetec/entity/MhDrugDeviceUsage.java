package com.meiyetec.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("mh_drug_device_usage")
public class MhDrugDeviceUsage {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private Long batchId;
    private Long drugDeviceId;
    private String itemName;
    private Long orderId;
    private String orderNo;
    private Long memberId;
    private String memberName;
    private Long doctorId;
    private String doctorName;
    private String doctorLicenseNo;
    private Integer qty;
    private String bodyPart;
    private String serialNo;
    private LocalDateTime usedAt;
    private String adverseReaction;
    private Integer hasAdverse;
    /** 1已使用 2已撤回 */
    private Integer status;
    private String operatorName;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
    @TableLogic
    private Integer deleted;
}