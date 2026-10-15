package com.meiyetec.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("payment_transaction_log")
public class PaymentTransactionLog {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private Long configId;
    private String channelCode;
    private String outTradeNo;
    /** 渠道流水号 */
    private String tradeNo;
    private BigDecimal amount;
    private BigDecimal fee;
    /** INIT / SUCCESS / FAILED / REFUNDED / CLOSED */
    private String status;
    private String requestPayload;
    private String responsePayload;
    private String errorCode;
    private String errorMessage;
    private String clientIp;
    private Integer costMs;

    private LocalDateTime createdAt;
}