package com.meiyetec.entity;
import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
/**
 * 数字人民币智能合约
 * 预付卡资金监管、限时储值、条件触发优惠券 
*/
@Data
@TableName("biz_dcep_smart_contract")
public class DcepSmartContract {    
@TableId(type = IdType.AUTO)    private Long id;    private Long tenantId;    
/** 合约名称 
*/    
private String contractName;    
/** 合约类型: prepaid_card_escrow/limited_storage/conditional_coupon 
*/    
private String contractType;    
/** 合约地址 
*/    
private String contractAddress;    
/** 合约代码hash 
*/    
private String contractCodeHash;    
/** 合约源代码(Solidity风格) 
*/    
private String contractCode;    
/** 关联钱包ID 
*/    private Long walletId;    
/** 关联客户ID 
*/    private Long memberId;    
/** 监管金额 
*/    private BigDecimal escrowAmount;    
/** 已释放金额 
*/    private BigDecimal releasedAmount;    
/** 已使用金额 
*/    private BigDecimal usedAmount;    
/** 已退还金额 
*/    private BigDecimal refundedAmount;    
/** 货币 
*/    
private String currency;    
/** 触发条件JSON:    
 * {    
 *   "type": "expire",    
 *   "deadline": "2026-12-31",    
 *   "minSpend": 0,    
 *   "merchantCategories": ["beauty"],    
 *   "conditions": [    
 *     {"trigger": "purchase","amount":">=100","action":"release"}    
 *   ]    
 * }     
*/    
private String triggerConditions;    
/** 合约状态: pending/active/completed/cancelled/expired 
*/    
private String status;    
/** 激活时间 
*/    private LocalDateTime activatedTime;    
/** 过期时间 
*/    private LocalDateTime expiredTime;    
/** 完成时间 
*/    private LocalDateTime completedTime;    
/** 自动执行次数 
*/    private Integer autoExecuteCount;    
/** 手动执行次数 
*/    private Integer manualExecuteCount;    
/** 合约事件日志 
*/    
private String eventLog;    
private String remark;    
@TableField(fill = FieldFill.INSERT)    private LocalDateTime createTime;    
@TableField(fill = FieldFill.INSERT_UPDATE)    private LocalDateTime updateTime;    @TableLogic    private Integer deleted;}
