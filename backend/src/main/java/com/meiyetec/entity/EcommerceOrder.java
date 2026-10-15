package com.meiyetec.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("ecommerce_order")
public class EcommerceOrder {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private String platform;
    private String externalOrderId;
    private Long localOrderId;
    private String orderNo;
    private String buyerName;
    private String buyerPhone;
    private BigDecimal amount;
    private BigDecimal shippingFee;
    private BigDecimal totalAmount;
    private String status;
    private String address;
    private String trackingNo;
    private String shippingStatus;
    private String itemsJson;
    private String rawPayload;
    private LocalDateTime orderTime;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}