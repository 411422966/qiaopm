package com.meiyetec.entity;
import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;
/**
 * 护理建议规则
 * 基于客户画像/标签的规则引擎，用于自动生成建议 
*/
@Data
@TableName("biz_care_suggestion_rule")
public class CareSuggestionRule {    
@TableId(type = IdType.AUTO)    private Long id;    private Long tenantId;    private Long storeId;    
/** 规则名称 
*/    
private String ruleName;    
/** 规则编码 
*/    
private String ruleCode;    
/** 触发条件类型: tag(标签)/skin_type(肤质)/age_range(年龄段)/season(季节)/service_history(服务历史) 
*/    
private String conditionType;    
/** 触发条件值(JSON) 
*/    
private String conditionValue;    
/** 关联模板ID 
*/    private Long templateId;    
/** 建议动作: recommend(推荐项目)/remind(提醒)/upsell(升级)/cross_sell(交叉销售) 
*/    
private String suggestionAction;    
/** 优先级(数字越大越优先) 
*/    private Integer priority;    
/** 冷却期(天) - 同一规则对同一客户冷却 
*/    private Integer cooldownDays;    
/** 状态: 0-停用, 1-启用 
*/    private Integer status;    
/** 备注 
*/    
private String remark;    
@TableField(fill = FieldFill.INSERT)    private LocalDateTime createTime;    
@TableField(fill = FieldFill.INSERT_UPDATE)    private LocalDateTime updateTime;    @TableLogic    private Integer deleted;    
@TableField(exist = false)    
private String templateName;}
