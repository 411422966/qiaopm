package com.meiyetec.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("payment_gateway")
public class PaymentGateway {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private String gatewayCode;
    private String gatewayName;
    private String merchantId;
    private String apiKey;
    private String secretKey;
    private String webhookSecret;
    private String currency;
    private Integer sandboxMode;
    private String testPublicKey;
    private String testSecretKey;
    private String supportedMethods;
    private String region;
    private String extraConfig;
    private String status;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}