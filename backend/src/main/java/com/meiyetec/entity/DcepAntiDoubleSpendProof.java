package com.meiyetec.entity;
import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;
/**
 * 数字人民币双离线防双花证据
 * 用于离线支付防欺诈追溯 
*/
@Data
@TableName("biz_dcep_anti_double_spend_proof")
public class DcepAntiDoubleSpendProof {    
@TableId(type = IdType.AUTO)    private Long id;    private Long tenantId;    
/** 钱包ID 
*/    private Long walletId;    
/** 钱包编号 
*/    
private String walletCode;    
/** 交易编号 
*/    
private String txNo;    
/** 序号/Nonce 
*/    private Long nonce;    
/** 防双花 Merkle 根 
*/    
private String merkleRoot;    
/** 双重签名 
*/    
private String dualSignature;    
/** 钱包证明 
*/    
private String walletProof;    
/** 商户证明 
*/    
private String merchantProof;    
/** 时间戳 
*/    private LocalDateTime timestamp;    
/** 状态: pending/verified/rejected 
*/    
private String status;    
/** 验证时间 
*/    private LocalDateTime verifyTime;    
private String verifierId;    
@TableField(fill = FieldFill.INSERT)    private LocalDateTime createTime;    @TableLogic    private Integer deleted;}
