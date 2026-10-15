package com.meiyetec.entity;
import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
/**
 * 肤质检测记录
 * 详细的皮肤检测数据（水分、油脂、弹性、毛孔等） 
*/
@Data
@TableName("biz_skin_detection")
public class SkinDetection {    
@TableId(type = IdType.AUTO)    private Long id;    private Long tenantId;    private Long storeId;    
/** 客户ID 
*/    private Long memberId;    
/** 客户姓名 
*/    
@TableField(exist = false)    
private String memberName;    
/** 检测编号 
*/    
private String detectionNo;    
/** 肤质类型: dry/oily/combination/sensitive/normal/mixed 
*/    
private String skinType;    
/** 综合得分(0-100) 
*/    private BigDecimal overallScore;    
/** 水分含量(0-100) 
*/    private BigDecimal moistureScore;    
/** 油脂含量(0-100) 
*/    private BigDecimal oilScore;    
/** 弹性(0-100) 
*/    private BigDecimal elasticityScore;    
/** 毛孔评分(0-100,数字越小越好) 
*/    private BigDecimal poreScore;    
/** 色素沉着评分(0-100) 
*/    private BigDecimal pigmentationScore;    
/** 皱纹评分(0-100) 
*/    private BigDecimal wrinkleScore;    
/** 敏感度评分(0-100) 
*/    private BigDecimal sensitivityScore;    
/** 痘痘问题等级: 0-无,1-轻微,2-中度,3-严重 
*/    private Integer acneLevel;    
/** 黑眼圈等级: 0-无,1-轻微,2-中度,3-严重 
*/    private Integer darkCircleLevel;    
/** 色斑数量 
*/    private Integer spotCount;    
/** 细纹数量 
*/    private Integer fineLineCount;    
/** 主诉问题JSON: ["acne","dark_circle","pigmentation"] 
*/    
private String mainConcerns;    
/** 诊断建议(文本) 
*/    
private String diagnosisAdvice;    
/** 检测设备: visia/wood_lamp/manual 
*/    
private String detectionDevice;    
/** 检测设备序列号 
*/    
private String deviceSerial;    
/** 检测图片JSON: ["url1","url2"] 
*/    
private String detectionImages;    
/** 检测门店ID 
*/    private Long checkStoreId;    
/** 检测技师ID 
*/    private Long checkEmployeeId;    
/** 检测技师姓名 
*/    
private String checkEmployeeName;    
/** 检测日期 
*/    private LocalDate detectionDate;    
/** 下次复检日期 
*/    private LocalDate nextCheckDate;    
/** 状态: pending/confirmed/archived 
*/    
private String status;    
/** 备注 
*/    
private String remark;    
@TableField(fill = FieldFill.INSERT)    private LocalDateTime createTime;    
@TableField(fill = FieldFill.INSERT_UPDATE)    private LocalDateTime updateTime;    @TableLogic    private Integer deleted;}
