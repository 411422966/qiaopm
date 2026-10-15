package com.meiyetec.entity;
import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
/**
 * 耗材消耗记录
 * 服务执行时实际消耗耗材的追溯记录 
*/
@Data
@TableName("biz_material_consumption_log")
public class MaterialConsumptionLog {    
@TableId(type = IdType.AUTO)    private Long id;    
/** 关联订单ID 
*/    private Long orderId;    
/** 订单号 
*/    
private String orderNo;    
/** 关联订单明细ID 
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
/** 耗材规格 
*/    
private String spec;    
/** 消耗数量 
*/    private BigDecimal quantity;    
/** 单位成本 
*/    private BigDecimal unitCost;    
/** 总成本 
*/    private BigDecimal totalCost;    
/** 是否使用替代耗材: 0-否, 1-是 
*/    private Integer isSubstitute;    
/** 替代的原始耗材ID 
*/    private Long originalProductId;    
/** 技师ID 
*/    private Long employeeId;    
/** 技师姓名 
*/    
private String employeeName;    
/** 客户ID 
*/    private Long memberId;    
/** 门店ID 
*/    private Long storeId;    
/** 消耗日期 
*/    private LocalDate consumptionDate;    
/** 创建时间 
*/    
@TableField(fill = FieldFill.INSERT)    private LocalDateTime createTime;}
