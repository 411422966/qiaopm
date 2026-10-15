package com.meiyetec.entity;
import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;
/**
 * 项目自动扣减审计日志
 * 每次服务执行的耗材扣减行为全程追溯 
*/
@Data
@TableName("biz_service_material_deduct_log")
public class ServiceMaterialDeductLog {    
@TableId(type = IdType.AUTO)    private Long id;    private Long tenantId;    private Long storeId;    
/** 关联订单ID 
*/    private Long orderId;    
/** 订单号 
*/    
private String orderNo;    
/** 订单明细ID 
*/    private Long orderItemId;    
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
/** 批次ID(具体消耗的库存批次) 
*/    private Long inventoryId;    
/** 批次号 
*/    
private String batchNo;    
/** 计划用量(根据绑定规则计算) 
*/    private BigDecimal plannedQuantity;    
/** 实际扣减数量 
*/    private BigDecimal actualQuantity;    
/** 单位成本 
*/    private BigDecimal unitCost;    
/** 总成本 
*/    private BigDecimal totalCost;    
/** 扣减策略: fefo/fifo/manual/substitute 
*/    
private String deductStrategy;    
/** 扣减前批次库存 
*/    private Integer beforeStock;    
/** 扣减后批次库存 
*/    private Integer afterStock;    
/** 是否使用替代: 0-否, 1-是 
*/    private Integer isSubstitute;    
/** 替代的原始耗材ID 
*/    private Long originalProductId;    
/** 扣减状态: success/failed/partial 
*/    
private String status;    
/** 失败原因 
*/    
private String failureReason;    
/** 技师ID 
*/    private Long employeeId;    
/** 技师姓名 
*/    
private String employeeName;    
/** 客户ID 
*/    private Long memberId;    
/** 是否乐观锁重试 
*/    private Integer retryCount;    
/** 事务ID(用于关联同一事务的多条记录) 
*/    
private String transactionId;    
/** 扣减时间 
*/    private LocalDateTime deductTime;    
@TableField(fill = FieldFill.INSERT)    private LocalDateTime createTime;    
@TableField(fill = FieldFill.INSERT_UPDATE)    private LocalDateTime updateTime;    @TableLogic    private Integer deleted;}
