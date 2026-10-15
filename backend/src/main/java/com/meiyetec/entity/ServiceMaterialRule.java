package com.meiyetec.entity;
import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;
/**
 * 项目耗材绑定规则
 * 配置每个服务项目需要的耗材明细（标准用量、损耗率等） 
*/
@Data
@TableName("biz_service_material_rule")
public class ServiceMaterialRule {    
@TableId(type = IdType.AUTO)    private Long id;    private Long tenantId;    private Long storeId;    
/** 服务ID 
*/    private Long serviceId;    
/** 服务名称 
*/    
@TableField(exist = false)    
private String serviceName;    
/** 耗材ID 
*/    private Long productId;    
/** 耗材名称 
*/    
@TableField(exist = false)    
private String productName;    
/** 耗材规格 
*/    
@TableField(exist = false)    
private String spec;    
/** 标准用量 
*/    private BigDecimal standardQuantity;    
/** 单位(ml/g/片等) 
*/    
private String unit;    
/** 损耗率(0-1,默认0.05表示5%损耗) 
*/    private BigDecimal wasteRate;    
/** 实际用量 = 标准用量 × (1 + 损耗率) 
*/    
@TableField(exist = false)    private BigDecimal actualQuantity;    
/** 是否必用: 0-否(可替代), 1-是 
*/    private Integer required;    
/** 是否启用FEFO(先过期先出): 0-否, 1-是 
*/    private Integer fefoEnabled;    
/** 备注 
*/    
private String remark;    
@TableField(fill = FieldFill.INSERT)    private LocalDateTime createTime;    
@TableField(fill = FieldFill.INSERT_UPDATE)    private LocalDateTime updateTime;    @TableLogic    private Integer deleted;}
