package com.meiyetec.entity;
import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;
/**
 * 提成模拟计算记录
 * 用于员工预览预期提成 
*/
@Data
@TableName("biz_commission_simulation")
public class CommissionSimulation {    
@TableId(type = IdType.AUTO)    private Long id;    
/** 员工ID 
*/    private Long employeeId;    
/** 员工姓名 
*/    
private String employeeName;    
/** 技师等级ID 
*/    private Long employeeLevelId;    
/** 模拟服务ID 
*/    private Long serviceId;    
/** 服务金额 
*/    private BigDecimal serviceAmount;    
/** 客户类型 
*/    
private String customerType;    
/** 模拟日期 
*/    
private String simulationDate;    
/** 命中的规则ID 
*/    private Long matchedRuleId;    
/** 命中的规则名称 
*/    
private String matchedRuleName;    
/** 命中的条件列表(JSON) 
*/    
private String matchedConditions;    
/** 计算方式: rate/fixed/tier 
*/    
private String calculationMethod;    
/** 应用的提成比例 
*/    private BigDecimal rate;    
/** 应用的固定金额 
*/    private BigDecimal fixedAmount;    
/** 最终提成金额 
*/    private BigDecimal commissionAmount;    
/** 计算明细JSON 
*/    
private String calculationDetail;    
/** 创建时间 
*/    
@TableField(fill = FieldFill.INSERT)    private LocalDateTime createTime;}
