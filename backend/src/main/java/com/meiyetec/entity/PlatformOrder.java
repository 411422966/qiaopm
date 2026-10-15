package com.meiyetec.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * R3.0-G P3-2：第三方平台订单（美团/抖音）
 */
@Data
@TableName("platform_order")
public class PlatformOrder {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private Long storeId;
    /** MEITUAN/DOUYIN/OTHER */
    private String platform;
    private String platformOrderId;
    private String platformDealId;
    private String dealName;
    private String customerName;
    private String customerPhone;
    private Integer quantity;
    private BigDecimal amount;
    private BigDecimal commission;
    private String verificationCode;
    /** PENDING/VERIFIED/REFUNDED/CANCELLED */
    private String status;
    private LocalDateTime verifiedAt;
    private LocalDateTime refundedAt;
    private String rawPayload;
    private Long memberId;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
    @TableLogic
    private Integer deleted;
}