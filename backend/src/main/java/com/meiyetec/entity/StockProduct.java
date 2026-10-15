package com.meiyetec.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * M08 商品/耗材实体
 */
@Data
@TableName("stock_product")
public class StockProduct {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private Long storeId;
    private String productCode;
    private String productName;
    private String category;
    private String spec;
    private String unit;
    private BigDecimal costPrice;
    private BigDecimal salePrice;
    private Integer minStock;
    private Integer maxStock;
    private Integer enableBatch;
    private Integer status;
    private String remark;

    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    @TableLogic
    private Integer deleted;
}