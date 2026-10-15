package com.meiyetec.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("mkt_coupon")
public class MktCoupon {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private Long storeId;
    private Long batchId;
    private String couponCode;
    private Long memberId;
    private String memberName;
    private String couponName;
    private String couponType;
    private BigDecimal discountAmount;
    private BigDecimal discountRate;
    private BigDecimal minConsume;
    private String discountScope;
    /** 1未使用 2已使用 3已过期 4已作废 */
    private Integer status;
    private Long usedOrderId;
    private LocalDateTime usedTime;
    private BigDecimal usedAmount;
    private LocalDate effectiveDate;
    private LocalDate expireDate;
    private LocalDateTime grantedAt;
    private String grantedBy;
    private String idempotencyKey;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
    @TableLogic
    private Integer deleted;
}
