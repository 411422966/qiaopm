package com.meiyetec.entity;
import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;
/**
 * 提成规则条件
 * 用于多维度条件匹配：服务类型、技师等级、客户类型、时段等 
*/
@Data
@TableName("biz_commission_condition")
public class CommissionCondition {    
@TableId(type = IdType.AUTO)    private Long id;    
/** 关联提成规则ID 
*/    private Long ruleId;    
/** 条件类型:    
 * service_category(项目分类)/service(具体项目)/employee_level(技师等级)    
 * /customer_type(客户类型)/member_level(会员等级)/time_slot(时段)    
 * /weekday(星期)/min_amount(最低金额)/max_amount(最高金额)     
*/    
private String conditionType;    
/** 条件操作符: eq/in/gte/lte/between 
*/    
private String operator;    
/** 条件值(JSON格式,根据conditionType含义不同) 
*/    
private String conditionValue;    
/** 条件显示名 
*/    
private String conditionLabel;    
/** 排序 
*/    private Integer sortOrder;    
@TableField(fill = FieldFill.INSERT)    private LocalDateTime createTime;    
@TableField(fill = FieldFill.INSERT_UPDATE)    private LocalDateTime updateTime;    @TableLogic    private Integer deleted;}
