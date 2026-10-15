package com.meiyetec.entity;
import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;
/**
 * 耗材替代规则
 * 当主要耗材库存不足时，可使用替代耗材 
*/
@Data
@TableName("biz_material_substitute")
public class MaterialSubstitute {
@TableId(type = IdType.AUTO)    private Long id;
/** 所属租户ID */
private Long tenantId;
/** 主耗材ID
*/    private Long mainProductId;
/** 主耗材名称 
*/    
@TableField(exist = false)    
private String mainProductName;    
/** 替代耗材ID 
*/    private Long substituteProductId;    
/** 替代耗材名称 
*/    
@TableField(exist = false)    
private String substituteProductName;    
/** 替代比例（1替代耗材 = 多少主耗材） 
*/    private BigDecimal substituteRatio;    
/** 触发条件: stock_low(库存不足)/price_high(价格过高)/manual(手动) 
*/    
private String triggerCondition;    
/** 最低库存阈值(主耗材低于该值时启用替代) 
*/    private Integer stockThreshold;    
/** 优先级 
*/    private Integer priority;    
/** 状态: 0-停用, 1-启用 
*/    private Integer status;    
/** 备注 
*/    
private String remark;    
@TableField(fill = FieldFill.INSERT)    private LocalDateTime createTime;    
@TableField(fill = FieldFill.INSERT_UPDATE)    private LocalDateTime updateTime;    @TableLogic    private Integer deleted;}
