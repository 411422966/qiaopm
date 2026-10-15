package com.meiyetec.entity;
import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;
/**
 * IoT 联动触发日志
 * 记录每次规则触发的执行情况 
*/
@Data
@TableName("biz_iot_trigger_log")
public class IotTriggerLog {    
@TableId(type = IdType.AUTO)    private Long id;    private Long tenantId;    
/** 规则ID 
*/    private Long ruleId;    
/** 规则名称 
*/    
private String ruleName;    
/** 设备ID 
*/    private Long deviceId;    
/** 数据ID 
*/    private Long dataId;    
/** 客户ID 
*/    private Long memberId;    
/** 触发条件 
*/    
private String triggerCondition;    
/** 实际指标值 
*/    
private String actualMetric;    
/** 执行状态: pending/success/failed/skipped 
*/    
private String status;    
/** 执行结果 
*/    
private String result;    
/** 错误信息 
*/    
private String errorMessage;    
/** 执行耗时(ms) 
*/    private Long executionMs;    private LocalDateTime triggerTime;    
@TableField(fill = FieldFill.INSERT)    private LocalDateTime createTime;    @TableLogic    private Integer deleted;}
