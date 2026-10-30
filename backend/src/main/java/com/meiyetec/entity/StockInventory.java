package com.meiyetec.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * M08 库存实体（含批次）
 */
@Data
@TableName("stock_inventory")
public class StockInventory {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private Long storeId;
    private Long productId;

    /**
     * 商品名称（不落库，来自 stock_product.product_name 关联查询）
     */
    @TableField(exist = false)
    private String productName;

    /**
     * 商品分类（不落库，来自 stock_product.category 关联查询）
     */
    @TableField(exist = false)
    private String category;

    private String batchNo;
    private Integer quantity;
    private LocalDate productionDate;
    private LocalDate expiryDate;
    private String supplier;
    private LocalDateTime lastInTime;
    private LocalDateTime lastOutTime;

    /** 乐观锁版本号（STOCK_FEFO_CAS 关键字段） */
    private Integer version;

    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    @TableLogic
    private Integer deleted;
}
