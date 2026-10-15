package com.meiyetec.entity;
import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
/**
 * 支付路由规则
 * 根据币种/金额/地区/支付方式选择最优网关 
*/
@Data
@TableName("biz_payment_route_rule")
public class PaymentRouteRule {    
@TableId(type = IdType.AUTO)    private Long id;    private Long tenantId;    
/** 规则名称 
*/    
private String ruleName;    
/** 优先级: 数字越大优先级越高 
*/    private Integer priority;    
/** 匹配货币 
*/    
private String currency;    
/** 匹配地区 
*/    
private String countryCode;    
/** 最小金额 
*/    private BigDecimal minAmount;    
/** 最大金额 
*/    private BigDecimal maxAmount;    
/** 支付方式 
*/    
private String paymentMethod;    
/** 目标网关编码 
*/    
private String gatewayCode;    
/** 手续费率(0-1) 
*/    private BigDecimal feeRate;    
/** 优先级权重: cost(费率优先)/speed(速度优先)/coverage(覆盖优先) 
*/    
private String priorityStrategy;    
/** 状态: 0-停用, 1-启用 
*/    private Integer status;    
private String remark;    
@TableField(fill = FieldFill.INSERT)    private LocalDateTime createTime;    
@TableField(fill = FieldFill.INSERT_UPDATE)    private LocalDateTime updateTime;    @TableLogic    private Integer deleted;}
