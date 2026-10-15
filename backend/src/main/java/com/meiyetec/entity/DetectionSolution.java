package com.meiyetec.entity;
import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;
/**
 * 检测项目方案
 * 配置针对不同问题的标准护理方案 
*/
@Data
@TableName("biz_detection_solution")
public class DetectionSolution {    
@TableId(type = IdType.AUTO)    private Long id;    private Long tenantId;    private Long storeId;    
/** 方案名称 
*/    
private String solutionName;    
/** 方案编码 
*/    
private String solutionCode;    
/** 适用类型: skin/hair/nail 
*/    
private String applyType;    
/** 触发条件JSON: ["sensitive","acne"] 
*/    
private String triggerConditions;    
/** 触发分数阈值(综合分低于此值触发) 
*/    private Integer scoreThreshold;    
/** 优先级: 1-10,数字越大越优先 
*/    private Integer priority;    
/** 推荐项目JSON: [{serviceId, serviceName, frequency}] 
*/    
private String recommendedServices;    
/** 推荐产品JSON: [{productId, productName, usage}] 
*/    
private String recommendedProducts;    
/** 居家护理建议 
*/    
private String homeCareAdvice;    
/** 治疗周期(天) 
*/    private Integer treatmentCycle;    
/** 预期效果 
*/    
private String expectedOutcome;    
/** 状态: 0-停用, 1-启用 
*/    private Integer status;    
private String remark;    
@TableField(fill = FieldFill.INSERT)    private LocalDateTime createTime;    
@TableField(fill = FieldFill.INSERT_UPDATE)    private LocalDateTime updateTime;    @TableLogic    private Integer deleted;}
