package com.meiyetec.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("subscription_billing")
public class SubscriptionBilling {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private Long subMemberId;
    private Long memberId;
    private Long planId;
    private LocalDate billingDate;
    private BigDecimal amount;
    private String paymentMethod;
    private String paymentStatus;
    private String billNo;
    private String transactionId;
    private String errorMessage;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    private LocalDateTime paidAt;
}