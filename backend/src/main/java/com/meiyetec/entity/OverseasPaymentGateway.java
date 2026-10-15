package com.meiyetec.entity;
import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;
/**
 * 海外支付网关适配
 * 统一管理 Stripe/Square/PayPal 等国际支付网关 
*/
@Data
@TableName("biz_overseas_payment_gateway")
public class OverseasPaymentGateway {    
@TableId(type = IdType.AUTO)    private Long id;    private Long tenantId;    private Long storeId;    
/** 网关编码: stripe/square/paypal/adyen/braintree 
*/    
private String gatewayCode;    
/** 网关名称 
*/    
private String gatewayName;    
/** API Base URL 
*/    
private String apiBaseUrl;    
/** Publishable Key / Client ID 
*/    
private String publishableKey;    
/** Secret Key (加密存储) 
*/    
private String secretKey;    
/** Webhook Secret 
*/    
private String webhookSecret;    
/** 商户ID 
*/    
private String merchantId;    
/** 账户ID 
*/    
private String accountId;    
/** 货币(JSON数组,支持的币种) 
*/    
private String supportedCurrencies;    
/** 支付方式(JSON: card/apple_pay/google_pay/alipay/wechat_pay) 
*/    
private String paymentMethods;    
/** 交易手续费率(0-1) 
*/    private java.math.BigDecimal feeRate;    
/** 固定费用 
*/    private java.math.BigDecimal fixedFee;    
/** 单笔限额 
*/    private java.math.BigDecimal maxAmountPerTransaction;    
/** 单日限额 
*/    private java.math.BigDecimal maxAmountPerDay;    
/** 沙箱/生产: sandbox/production 
*/    
private String environment;    
/** API 版本 
*/    
private String apiVersion;    
/** 国家代码 
*/    
private String countryCode;    
/** 状态: 0-停用, 1-启用 
*/    private Integer status;    
/** 健康状态: healthy/degraded/unhealthy 
*/    
private String healthStatus;    
/** 最新健康检查时间 
*/    private LocalDateTime lastHealthCheck;    
/** 配置元数据JSON 
*/    
private String metadata;    
private String remark;    
@TableField(fill = FieldFill.INSERT)    private LocalDateTime createTime;    
@TableField(fill = FieldFill.INSERT_UPDATE)    private LocalDateTime updateTime;    @TableLogic    private Integer deleted;}
