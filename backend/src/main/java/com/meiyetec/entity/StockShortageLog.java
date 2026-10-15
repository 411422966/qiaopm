package com.meiyetec.entity;
import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;
/**
 * 库存不足告警
 * 扣减过程中发现库存不足时记录并告警 
*/
@Data
@TableName("biz_stock_shortage_log")
public class StockShortageLog {    
@TableId(type = IdType.AUTO)    private Long id;    private Long tenantId;    private Long storeId;    
/** 订单ID 
*/    private Long orderId;    
/** 订单号 
*/    
private String orderNo;    
/** 服务ID 
*/    private Long serviceId;    
/** 服务名称 
*/    
private String serviceName;    
/** 耗材ID 
*/    private Long productId;    
/** 耗材名称 
*/    
private String productName;    
/** 需求量 
*/    private BigDecimal requiredQuantity;    
/** 当前库存 
*/    private BigDecimal currentStock;    
/** 缺口数量 
*/    private BigDecimal shortageQuantity;    
/** 是否已替代 
*/    private Integer substituted;    
/** 替代耗材ID 
*/    private Long substituteProductId;    
/** 告警级别: critical/warning 
*/    
private String alertLevel;    
/** 状态: pending/resolved 
*/    
private String status;    
/** 备注 
*/    
private String remark;    
@TableField(fill = FieldFill.INSERT)    private LocalDateTime createTime;    
@TableField(fill = FieldFill.INSERT_UPDATE)    private LocalDateTime updateTime;    @TableLogic    private Integer deleted;}
