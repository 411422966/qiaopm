package com.meiyetec.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("mkt_order_discount")
public class MktOrderDiscount {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private Long storeId;
    private Long orderId;
    private String orderNo;
    /** PROMOTION/COUPON/POINTS/MANUAL */
    private String sourceType;
    private Long sourceId;
    private String sourceName;
    private BigDecimal discountAmount;
    private Long couponId;
    private Integer pointsUsed;
    /** 1已应用 2已撤销 */
    private Integer status;
    private BigDecimal refundAmount;
    private Long operatorId;
    private String operatorName;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
    @TableLogic
    private Integer deleted;
}
