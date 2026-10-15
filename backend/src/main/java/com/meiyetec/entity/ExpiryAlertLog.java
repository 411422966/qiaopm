package com.meiyetec.entity;
import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDate;
import java.time.LocalDateTime;
/**
 * 效期预警日志
 * 每次扫描产生的预警记录 
*/
@Data
@TableName("biz_expiry_alert_log")
public class ExpiryAlertLog {    
@TableId(type = IdType.AUTO)    private Long id;    private Long tenantId;    private Long storeId;    
/** 库存批次ID 
*/    private Long inventoryId;    
/** 耗材ID 
*/    private Long productId;    
/** 耗材名称 
*/    
private String productName;    
/** 批次号 
*/    
private String batchNo;    
/** 当前数量 
*/    private Integer quantity;    
/** 生产日期 
*/    private LocalDate productionDate;    
/** 过期日期 
*/    private LocalDate expiryDate;    
/** 距离过期天数(负数表示已过期) 
*/    private Integer daysToExpiry;    
/** 预警级别: critical(严重)/warning(警告)/normal(正常) 
*/    
private String alertLevel;    
/** 预警类型: near_expiry(临期)/expired(已过期)/stale(积压) 
*/    
private String alertType;    
/** 预警内容 
*/    
private String alertMessage;    
/** 状态: pending(待处理)/processing(处理中)/resolved(已处理)/ignored(已忽略) 
*/    
private String status;    
/** 处理方式: dispose(销毁)/return(退货)/promote(促销)/transfer(调拨)/use_first(先用) 
*/    
private String handleMethod;    
/** 处理人ID 
*/    private Long handlerId;    
/** 处理人姓名 
*/    
private String handlerName;    
/** 处理时间 
*/    private LocalDateTime handleTime;    
/** 处理备注 
*/    
private String handleRemark;    
/** 通知状态: 0-未通知, 1-已通知 
*/    private Integer notified;    
@TableField(fill = FieldFill.INSERT)    private LocalDateTime createTime;    
@TableField(fill = FieldFill.INSERT_UPDATE)    private LocalDateTime updateTime;    @TableLogic    private Integer deleted;}
