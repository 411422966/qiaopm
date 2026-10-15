package com.meiyetec.entity;
import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
/**
 * 效期处理记录
 * 临期/过期耗材的处理流水（销毁/退货/促销/调拨） 
*/
@Data
@TableName("biz_expiry_handle_log")
public class ExpiryHandleLog {    
@TableId(type = IdType.AUTO)    private Long id;    private Long tenantId;    private Long storeId;    
/** 关联预警日志ID 
*/    private Long alertLogId;    
/** 批次ID 
*/    private Long inventoryId;    
/** 耗材ID 
*/    private Long productId;    
/** 耗材名称 
*/    
private String productName;    
/** 批次号 
*/    
private String batchNo;    
/** 处理方式: dispose(销毁)/return(退货)/promote(促销)/transfer(调拨)/use_first(先用) 
*/    
private String handleMethod;    
/** 处理数量 
*/    private Integer handleQuantity;    
/** 处理单价 
*/    private BigDecimal unitPrice;    
/** 损失金额 
*/    private BigDecimal lossAmount;    
/** 处理日期 
*/    private LocalDate handleDate;    
/** 处理原因 
*/    
private String handleReason;    
/** 操作人ID 
*/    private Long operatorId;    
/** 操作人姓名 
*/    
private String operatorName;    
/** 凭证(JSON) 
*/    
private String evidence;    
/** 备注 
*/    
private String remark;    
@TableField(fill = FieldFill.INSERT)    private LocalDateTime createTime;    
@TableField(fill = FieldFill.INSERT_UPDATE)    private LocalDateTime updateTime;    @TableLogic    private Integer deleted;}
