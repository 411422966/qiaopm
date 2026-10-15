package com.meiyetec.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("dianping_order")
public class DianpingOrder {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private String shopId;
    private String dealId;
    private String dpOrderId;
    private String buyerPhone;
    private String buyerName;
    private BigDecimal amount;
    private Integer quantity;
    private String serialNo;
    private String verifyCode;
    private String status;
    private LocalDateTime consumeTime;
    private Long localBookingId;
    private Long localMemberId;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}