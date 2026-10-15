package com.meiyetec.entity;
import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
/**
 * 技师等级评定记录
 * 记录每次等级评定的触发条件和结果 
*/
@Data
@TableName("biz_level_evaluation")
public class LevelEvaluation {    
@TableId(type = IdType.AUTO)    private Long id;    
/** 员工ID 
*/    private Long employeeId;    
/** 原等级ID 
*/    private Long fromLevelId;    
/** 评定等级ID 
*/    private Long toLevelId;    
/** 评定类型: auto(自动)/manual(手动) 
*/    
private String evaluationType;    
/** 评定结果: upgrade(晋升)/downgrade(降级)/keep(不变) 
*/    
private String result;    
/** 评定日期 
*/    private LocalDate evaluationDate;    
/** 当前累计业绩(元) 
*/    private BigDecimal totalSales;    
/** 当前好评率(0-100) 
*/    private BigDecimal ratingScore;    
/** 当前服务月数 
*/    private Integer serviceMonths;    
/** 投诉率(0-100) 
*/    private BigDecimal complaintRate;    
/** 评定说明 
*/    
private String evaluationNote;    
/** 操作人ID 
*/    private Long operatorId;    
/** 操作人姓名 
*/    
private String operatorName;    
/** 评定状态: pending(待生效)/active(已生效)/reverted(已撤销) 
*/    
private String status;    
/** 生效时间 
*/    private LocalDateTime effectiveTime;    private Long storeId;    
@TableField(fill = FieldFill.INSERT)    private LocalDateTime createTime;    
@TableField(fill = FieldFill.INSERT_UPDATE)    private LocalDateTime updateTime;    @TableLogic    private Integer deleted;    
@TableField(exist = false)    
private String employeeName;    
@TableField(exist = false)    
private String fromLevelName;    
@TableField(exist = false)    
private String toLevelName;}
