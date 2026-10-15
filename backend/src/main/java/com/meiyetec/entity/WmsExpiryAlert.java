package com.meiyetec.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("wms_expiry_alert")
public class WmsExpiryAlert {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private Long warehouseId;
    private String warehouseName;
    private Long productId;
    private String productName;
    private String batchNo;
    private Integer quantity;
    private LocalDate expiryDate;
    private Integer daysRemaining;
    /** EXPIRED / EXPIRING_30 / EXPIRING_60 / EXPIRING_90 */
    private String alertLevel;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}