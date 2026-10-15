package com.meiyetec.entity;
import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;
/**
 * 耗材用量分组
 * 同一服务在不同场景下的差异化用量配置（如高级技师额外消耗）
*/
@Data
@TableName("biz_service_material_group")
public class ServiceMaterialGroup {
@TableId(type = IdType.AUTO)    private Long id;
/** 所属租户ID */
private Long tenantId;
/** 关联服务ID
*/
    private Long serviceId;
/** 分组名称(场景) 
*/    
private String groupName;    
/** 分组维度: employee_level(技师等级)/customer_type(客户类型)/service_duration(服务时长) 
*/    
private String dimension;    
/** 维度值(JSON) 
*/    
private String dimensionValue;    
/** 优先级 
*/    private Integer priority;    
/** 备注 
*/    
private String remark;    
@TableField(fill = FieldFill.INSERT)    private LocalDateTime createTime;    
@TableField(fill = FieldFill.INSERT_UPDATE)    private LocalDateTime updateTime;    @TableLogic    private Integer deleted;    
@TableField(exist = false)    
private String serviceName;    
@TableField(exist = false)    private Integer itemCount;}
