package com.meiyetec.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("payment_config")
public class PaymentConfig {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private String configCode;
    private String configName;
    /** WECHAT / ALIPAY / UNIONPAY / DCEP / BANK / CASH / WALLET / CARD */
    private String channelCode;
    private String merchantId;
    private String appId;
    private String apiKey;
    private String apiSecret;
    private String certPath;
    private String privateKey;
    private String publicKey;
    private String notifyUrl;
    private String returnUrl;
    /** 手续费率 0.6% */
    private BigDecimal feeRate;
    /** PERCENTAGE / FIXED */
    private String feeType;
    private BigDecimal minFee;
    private BigDecimal maxFee;
    private BigDecimal dailyLimit;
    private BigDecimal monthlyLimit;
    /** SANDBOX / PROD / MOCK */
    private String env;
    /** ACTIVE / DISABLED / PENDING */
    private String status;
    /** JSON */
    private String extraConfig;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}