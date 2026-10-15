package com.meiyetec.entity;
import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;
/**
 * 智能合约执行日志 
*/
@Data
@TableName("biz_dcep_contract_execution_log")
public class DcepContractExecutionLog {    
@TableId(type = IdType.AUTO)    private Long id;    private Long tenantId;    
/** 合约ID 
*/    private Long contractId;    
/** 合约名称 
*/    
private String contractName;    
/** 执行类型: auto/manual 
*/    
private String executeType;    
/** 执行动作: release/refund/coupon_grant/cancel 
*/    
private String action;    
/** 执行金额 
*/    private BigDecimal amount;    
/** 执行结果: success/failed/skipped 
*/    
private String status;    
/** 触发条件快照 
*/    
private String triggerSnapshot;    
/** 执行结果描述 
*/    
private String result;    
/** 错误信息 
*/    
private String errorMessage;    
/** 关联交易ID 
*/    
private String transactionNo;    
/** 区块号(模拟) 
*/    private Long blockNumber;    
/** Gas消耗(模拟) 
*/    private Long gasUsed;    private LocalDateTime executeTime;    
@TableField(fill = FieldFill.INSERT)    private LocalDateTime createTime;    @TableLogic    private Integer deleted;}
