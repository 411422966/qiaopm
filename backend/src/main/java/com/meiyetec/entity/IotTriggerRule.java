package com.meiyetec.entity;
import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;
/**
 * IoT 联动触发规则
 * 当设备数据满足条件时触发业务动作 
*/
@Data
@TableName("biz_iot_trigger_rule")
public class IotTriggerRule {    
@TableId(type = IdType.AUTO)    private Long id;    private Long tenantId;    private Long storeId;    
/** 规则名称 
*/    
private String ruleName;    
/** 设备类型(适用范围) 
*/    
private String deviceType;    
/** 数据类型 
*/    
private String dataType;    
/** 触发条件JSON:    
 * {    
 *   "operator": "lt",       
// lt/gt/eq/between    
 *   "metric": "水分",    
 *   "value": 30,    
 *   "value2": 50    
 * }     
*/    
private String triggerCondition;    
/** 触发动作JSON:    
 * {    
 *   "action": "send_coupon",    
 *   "params": {"templateId":1,"amount":50}    
 * }     
*/    
private String triggerAction;    
/** 动作类型: send_coupon/send_sms/create_followup/update_member_tag/notify_employee 
*/    
private String actionType;    
/** 优先级 
*/    private Integer priority;    
/** 是否启用: 0-否, 1-是 
*/    private Integer enabled;    
/** 触发次数 
*/    private Integer triggerCount;    
/** 最后触发时间 
*/    private LocalDateTime lastTriggerTime;    
private String remark;    
@TableField(fill = FieldFill.INSERT)    private LocalDateTime createTime;    
@TableField(fill = FieldFill.INSERT_UPDATE)    private LocalDateTime updateTime;    @TableLogic    private Integer deleted;}
