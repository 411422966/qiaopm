package com.meiyetec.entity;
import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;
/**
 * 综合检测报告
 * 客户肤质/发质/甲面综合检测报告 
*/
@Data
@TableName("biz_detection_report")
public class DetectionReport {    
@TableId(type = IdType.AUTO)    private Long id;    private Long tenantId;    private Long storeId;    private Long memberId;    
@TableField(exist = false)    
private String memberName;    
/** 报告编号 
*/    
private String reportNo;    
/** 报告类型: skin/hair/nail/comprehensive 
*/    
private String reportType;    
/** 关联的肤质检测ID 
*/    private Long skinDetectionId;    
/** 关联的发质检测ID 
*/    private Long hairDetectionId;    
/** 关联的甲面检测ID 
*/    private Long nailDetectionId;    
/** 综合评分(0-100) 
*/    private BigDecimal overallScore;    
/** 综合肤质判断 
*/    
private String overallAssessment;    
/** 优先级问题(JSON数组,按严重程度排序) 
*/    
private String priorityConcerns;    
/** 推荐项目方案(JSON) 
*/    
private String recommendedServices;    
/** 推荐产品方案(JSON) 
*/    
private String recommendedProducts;    
/** 居家护理建议 
*/    
private String homeCareAdvice;    
/** 治疗周期(天) 
*/    private Integer treatmentCycle;    
/** 预期改善效果 
*/    
private String expectedOutcome;    
/** 报告PDF路径 
*/    
private String reportPdfUrl;    
/** 报告图片(JSON) 
*/    
private String reportImages;    
/** 客户是否查看: 0-否, 1-是 
*/    private Integer customerViewed;    
/** 客户反馈 
*/    
private String customerFeedback;    
/** 检测技师ID 
*/    private Long checkEmployeeId;    
private String checkEmployeeName;    
/** 报告生成时间 
*/    private LocalDateTime generateTime;    
/** 报告状态: draft/final/shared/archived 
*/    
private String status;    
private String remark;    
@TableField(fill = FieldFill.INSERT)    private LocalDateTime createTime;    
@TableField(fill = FieldFill.INSERT_UPDATE)    private LocalDateTime updateTime;    @TableLogic    private Integer deleted;}
