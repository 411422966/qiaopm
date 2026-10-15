package com.meiyetec.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("wms_warehouse_threshold")
public class WmsWarehouseThreshold {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private Long warehouseId;
    private Long productId;
    private Integer minStock;
    private Integer maxStock;
    private Integer safetyDays;
    private Integer enabled;
    private String operatorName;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}