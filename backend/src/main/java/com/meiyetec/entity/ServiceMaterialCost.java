package com.meiyetec.entity;
import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;
/**
 * 耗材成本统计（按服务）
 * 记录每个服务的耗材成本及成本率 
*/
@Data
@TableName("biz_service_material_cost")
public class ServiceMaterialCost {    
@TableId(type = IdType.AUTO)    private Long id;    
/** 服务ID 
*/    private Long serviceId;    
/** 服务名称 
*/    
private String serviceName;    
/** 服务售价 
*/    private BigDecimal servicePrice;    
/** 单次耗材成本 
*/    private BigDecimal materialCost;    
/** 成本占比(%) 
*/    private BigDecimal costRatio;    
/** 包含的耗材数量 
*/    private Integer itemCount;    
/** 必需耗材数量 
*/    private Integer requiredCount;    
/** 统计周期(YYYY-MM) 
*/    
private String statMonth;    
/** 更新时间 
*/    
@TableField(fill = FieldFill.INSERT_UPDATE)    private LocalDateTime updateTime;    
@TableField(fill = FieldFill.INSERT)    private LocalDateTime createTime;    @TableLogic    private Integer deleted;}
