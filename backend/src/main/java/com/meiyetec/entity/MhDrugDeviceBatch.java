package com.meiyetec.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("mh_drug_device_batch")
public class MhDrugDeviceBatch {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private Long drugDeviceId;
    private String batchNo;
    private String serialNo;
    private LocalDate productionDate;
    private LocalDate expireDate;
    private Long supplierId;
    private String supplierName;
    private String purchaseOrderNo;
    private Integer inboundQty;
    private Integer usedQty;
    private Integer remainingQty;
    private BigDecimal unitPrice;
    /** 1正常 2冻结 3过期 4召回 */
    private Integer status;
    private String freezeReason;
    private LocalDateTime recallAt;
    private String recallReason;
    private Long storeId;
    private String location;
    private Long operatorId;
    private String operatorName;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
    @TableLogic
    private Integer deleted;
}