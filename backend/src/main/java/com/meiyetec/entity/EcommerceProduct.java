package com.meiyetec.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("ecommerce_product")
public class EcommerceProduct {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private String platform;
    private String externalProductId;
    private Long localProductId;
    private String productName;
    private String sku;
    private BigDecimal price;
    private Integer stock;
    private String status;
    private LocalDateTime lastSyncAt;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}