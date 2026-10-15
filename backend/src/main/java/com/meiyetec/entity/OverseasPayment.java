package com.meiyetec.entity;
import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;
/**
 * 海外支付交易
 * 客户在系统内通过海外支付网关下单的记录 
*/
@Data
@TableName("biz_overseas_payment")
public class OverseasPayment {    
@TableId(type = IdType.AUTO)    private Long id;    private Long tenantId;    private Long storeId;    
/** 业务订单ID 
*/    private Long orderId;    
/** 业务订单类型: service_order/product_order/recharge 
*/    
private String orderType;    
/** 业务订单号 
*/    
private String orderNo;    
/** 客户ID 
*/    private Long memberId;    
/** 客户姓名 
*/    
private String memberName;    
/** 客户邮箱(海外支付必需) 
*/    
private String memberEmail;    
/** 网关编码 
*/    
private String gatewayCode;    
/** 网关支付意图ID/PaymentIntent ID 
*/    
private String gatewayIntentId;    
/** 网关支付ID 
*/    
private String gatewayPaymentId;    
/** 网关客户端密钥 
*/    
private String gatewayClientSecret;    
/** 交易金额 
*/    private BigDecimal amount;    
/** 货币 
*/    
private String currency;    
/** 手续费 
*/    private BigDecimal fee;    
/** 实际到账金额 
*/    private BigDecimal netAmount;    
/** 支付方式: card/apple_pay/google_pay/ideal/sofort 
*/    
private String paymentMethod;    
/** 支付方式详情 
*/    
private String paymentMethodDetail;    
/** 状态: pending/succeeded/failed/canceled/refunded/partially_refunded 
*/    
private String status;    
/** 失败原因 
*/    
private String failureMessage;    
/** 退款金额 
*/    private BigDecimal refundAmount;    
/** 退款原因 
*/    
private String refundReason;    
/** 退款时间 
*/    private LocalDateTime refundTime;    
/** 支付确认时间 
*/    private LocalDateTime paidAt;    
/** 来源: web/mobile/pos/mini_program 
*/    
private String source;    
/** IP地址 
*/    
private String ipAddress;    
/** 设备指纹 
*/    
private String deviceFingerprint;    
/** 3DS验证结果 
*/    
private String threeDsResult;    
/** 防欺诈评分 
*/    private BigDecimal fraudScore;    
/** 地区/国家 
*/    
private String countryCode;    
/** 扩展数据JSON 
*/    
private String metadata;    
@TableField(fill = FieldFill.INSERT)    private LocalDateTime createTime;    
@TableField(fill = FieldFill.INSERT_UPDATE)    private LocalDateTime updateTime;    @TableLogic    private Integer deleted;}
