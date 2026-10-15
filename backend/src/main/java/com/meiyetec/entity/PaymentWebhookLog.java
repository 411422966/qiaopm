package com.meiyetec.entity;
import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;
/**
 * 支付Webhook回调日志
 * 记录所有网关推送的回调事件 
*/
@Data
@TableName("biz_payment_webhook_log")
public class PaymentWebhookLog {    
@TableId(type = IdType.AUTO)    private Long id;    private Long tenantId;    
/** 网关编码 
*/    
private String gatewayCode;    
/** 事件类型: payment.succeeded/payment.failed/charge.refunded 等 
*/    
private String eventType;    
/** 网关事件ID(用于幂等) 
*/    
private String gatewayEventId;    
/** 网关关联的支付ID 
*/    
private String gatewayPaymentId;    
/** 事件载荷 
*/    
private String payload;    
/** 签名(用于验签) 
*/    
private String signature;    
/** 验证状态: 0-失败, 1-成功 
*/    private Integer verifyStatus;    
/** 处理状态: pending/processed/failed/skipped 
*/    
private String processStatus;    
/** 处理时间 
*/    private LocalDateTime processTime;    
/** 重试次数 
*/    private Integer retryCount;    
/** 错误信息 
*/    
private String errorMessage;    
/** 接收时间 
*/    private LocalDateTime receiveTime;    
/** 源IP 
*/    
private String sourceIp;    
@TableField(fill = FieldFill.INSERT)    private LocalDateTime createTime;    @TableLogic    private Integer deleted;}
