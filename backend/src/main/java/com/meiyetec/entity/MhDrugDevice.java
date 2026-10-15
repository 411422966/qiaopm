package com.meiyetec.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("mh_drug_device")
public class MhDrugDevice {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private Long brandId;
    private String itemCode;
    private String itemName;
    /** DRUG/DEVICE/CONSUMABLE */
    private String itemType;
    private String registrationNo;
    private String specification;
    private String manufacturer;
    private String category;
    private String storageCondition;
    private Integer shelfLifeDays;
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