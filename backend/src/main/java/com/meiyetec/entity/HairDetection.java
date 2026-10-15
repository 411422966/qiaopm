package com.meiyetec.entity;
import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
/**
 * 发质检测记录
 * 头发的健康状况详细数据 
*/
@Data
@TableName("biz_hair_detection")
public class HairDetection {    
@TableId(type = IdType.AUTO)    private Long id;    private Long tenantId;    private Long storeId;    private Long memberId;    
@TableField(exist = false)    
private String memberName;    
/** 检测编号 
*/    
private String detectionNo;    
/** 发质类型: dry/oily/combination/damaged/normal 
*/    
private String hairType;    
/** 综合得分(0-100) 
*/    private BigDecimal overallScore;    
/** 水分含量(0-100) 
*/    private BigDecimal moistureScore;    
/** 油脂含量(0-100) 
*/    private BigDecimal oilScore;    
/** 弹性(0-100) 
*/    private BigDecimal elasticityScore;    
/** 直径(细/中/粗) 
*/    
private String diameter;    
/** 密度(稀疏/正常/浓密) 
*/    
private String density;    
/** 损伤程度(0-100,数字越大越严重) 
*/    private BigDecimal damageScore;    
/** 断裂风险(0-100) 
*/    private BigDecimal breakageRisk;    
/** 头皮屑等级: 0-无,1-轻微,2-中度,3-严重 
*/    private Integer dandruffLevel;    
/** 头皮敏感度(0-100) 
*/    private BigDecimal scalpSensitivity;    
/** 脱发风险(0-100) 
*/    private BigDecimal hairLossRisk;    
/** 分叉程度(0-100) 
*/    private BigDecimal splitEndScore;    
/** 染色次数(近一年) 
*/    private Integer dyeCount;    
/** 烫发次数(近一年) 
*/    private Integer permCount;    
/** 主诉问题JSON: ["hair_loss","dandruff","dryness"] 
*/    
private String mainConcerns;    
/** 诊断建议 
*/    
private String diagnosisAdvice;    
/** 检测设备: microscope/wood_lamp/manual 
*/    
private String detectionDevice;    
/** 检测图片JSON 
*/    
private String detectionImages;    private Long checkStoreId;    private Long checkEmployeeId;    
private String checkEmployeeName;    private LocalDate detectionDate;    private LocalDate nextCheckDate;    
private String status;    
private String remark;    
@TableField(fill = FieldFill.INSERT)    private LocalDateTime createTime;    
@TableField(fill = FieldFill.INSERT_UPDATE)    private LocalDateTime updateTime;    @TableLogic    private Integer deleted;}
