package com.meiyetec.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * R3.0-D：多仓管理 — 调拨单（仓库间库存转移）
 */
@Data
@TableName("wms_stock_transfer")
public class WmsStockTransfer {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private String transferNo;
    private Long productId;
    private String productName;
    private String batchNo;
    private Integer quantity;
    private Long fromWarehouseId;
    private Long toWarehouseId;
    private Long fromStoreId;
    private Long toStoreId;
    /** PENDING/SHIPPED/RECEIVED/CANCELLED */
    private String status;
    private Long operatorId;
    private String operatorName;
    private String remark;
    private LocalDateTime shippedAt;
    private LocalDateTime receivedAt;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
    @TableLogic
    private Integer deleted;
}