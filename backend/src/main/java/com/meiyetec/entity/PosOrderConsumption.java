package com.meiyetec.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 订单-消耗幂等记录（防止订单支付成功后被重复扣减库存）
 * idempotency_key 形如：pos_order_consume:{tenantId}:{orderId}:{serviceId}:{productId}
 */
@Data
@TableName("pos_order_consumption")
public class PosOrderConsumption {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long tenantId;
    private Long orderId;
    private Long stockRecordId;
    private Long productId;
    private Long serviceId;
    private Integer quantity;
    private String idempotencyKey;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
