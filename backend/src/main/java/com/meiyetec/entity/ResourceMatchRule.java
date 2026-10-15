package com.meiyetec.entity;
import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;
/**
 * 房间-技师-服务匹配规则
 * 配置某些服务需要的房间类型、技师技能等 
*/
@Data
@TableName("biz_resource_match_rule")
public class ResourceMatchRule {    
@TableId(type = IdType.AUTO)    private Long id;    private Long tenantId;    private Long storeId;    
/** 服务ID 
*/    private Long serviceId;    
/** 服务名称 
*/    
private String serviceName;    
/** 所需房间类型JSON: ["vip","facial"] 
*/    
private String requiredRoomTypes;    
/** 最低房间等级: 1-9 
*/    private Integer minRoomLevel;    
/** 所需技师技能标签JSON: ["facial","anti_aging"] 
*/    
private String requiredSkills;    
/** 最低技师等级 
*/    private Integer minTechnicianLevel;    
/** 单次服务时长(分钟) 
*/    private Integer durationMinutes;    
/** 所需技师人数 
*/    private Integer requiredTechnicianCount;    
/** 房间使用人数 
*/    private Integer requiredRoomCapacity;    
/** 服务价格 
*/    private BigDecimal price;    
/** 是否需要双技师: 0-否, 1-是 
*/    private Integer needDualTechnician;    
/** 备注 
*/    
private String remark;    
@TableField(fill = FieldFill.INSERT)    private LocalDateTime createTime;    
@TableField(fill = FieldFill.INSERT_UPDATE)    private LocalDateTime updateTime;    @TableLogic    private Integer deleted;}
