package com.meiyetec.entity;
import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;
/**
 * 数字人民币离线交易
 * 支持双离线场景(消费者+商户都无网络) 
*/
@Data
@TableName("biz_dcep_offline_transaction")
public class DcepOfflineTransaction {    
@TableId(type = IdType.AUTO)    private Long id;    private Long tenantId;    private Long storeId;    
/** 钱包ID 
*/    private Long walletId;    
/** 钱包编号 
*/    
private String walletCode;    
/** 交易编号(离线生成,含UUID) 
*/    
private String offlineTxNo;    
/** 离线交易凭证 
*/    
private String offlineCert;    
/** 离线签名 
*/    
private String offlineSignature;    
/** 交易金额 
*/    private BigDecimal amount;    
/** 离线余额(交易后) 
*/    private BigDecimal offlineBalance;    
/** 交易类型: consume/recharge/refund 
*/    
private String txType;    
/** 商户号 
*/    
private String merchantId;    
/** 商户名称 
*/    
private String merchantName;    
/** 关联订单号 
*/    
private String orderNo;    
/** 双离线标志: 1-双方均离线, 0-部分在线 
*/    private Integer dualOffline;    
/** 同步状态: pending/synced/confirmed/failed/disputed 
*/    
private String syncStatus;    
/** 同步时间 
*/    private LocalDateTime syncTime;    
/** 同步尝试次数 
*/    private Integer syncAttempts;    
/** 冲突状态: 0-无冲突, 1-已检测冲突 
*/    private Integer conflictDetected;    
/** 冲突类型: double_spend/balance_overflow 
*/    
private String conflictType;    
/** 冲突解决方式 
*/    
private String conflictResolution;    
/** 防双花证据 
*/    
private String antiDoubleSpendProof;    
/** 交易时间(离线) 
*/    private LocalDateTime offlineTxTime;    
/** GPS 位置(交易发生地) 
*/    
private String geoLocation;    
/** 备注 
*/    
private String remark;    
@TableField(fill = FieldFill.INSERT)    private LocalDateTime createTime;    
@TableField(fill = FieldFill.INSERT_UPDATE)    private LocalDateTime updateTime;    @TableLogic    private Integer deleted;}
