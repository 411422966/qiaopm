package com.meiyetec.entity;
import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;
/**
 * 提成阶梯规则
 * 用于阶梯式提成：达到某个金额区间使用对应的提成比例/金额 
*/
@Data
@TableName("biz_commission_tier")
public class CommissionTier {    
@TableId(type = IdType.AUTO)    private Long id;    
/** 关联提成规则ID 
*/    private Long ruleId;    
/** 阶梯起始金额 
*/    private BigDecimal tierStart;    
/** 阶梯结束金额(null表示不封顶) 
*/    private BigDecimal tierEnd;    
/** 阶梯计算方式: rate/fixed 
*/    
private String calculationMethod;    
/** 提成比例/固定金额 
*/    private BigDecimal rate;    
/** 阶梯说明 
*/    
private String description;    
/** 排序 
*/    private Integer sortOrder;    
@TableField(fill = FieldFill.INSERT)    private LocalDateTime createTime;    
@TableField(fill = FieldFill.INSERT_UPDATE)    private LocalDateTime updateTime;    @TableLogic    private Integer deleted;}
