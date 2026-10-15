package com.meiyetec.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("payment_intent")
public class PaymentIntent {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private String intentNo;
    private String gatewayCode;
    private Long orderId;
    private String orderNo;
    private BigDecimal amount;
    private String currency;
    private String method;
    private String intentStatus;
    private String clientSecret;
    private String transactionId;
    private Integer installments;
    private String errorCode;
    private String errorMessage;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    private LocalDateTime paidAt;
}