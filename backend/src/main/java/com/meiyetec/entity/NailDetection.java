package com.meiyetec.entity;
import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
/**
 * 甲面检测记录
 * 美甲相关的指甲健康数据 
*/
@Data
@TableName("biz_nail_detection")
public class NailDetection {    
@TableId(type = IdType.AUTO)    private Long id;    private Long tenantId;    private Long storeId;    private Long memberId;    
@TableField(exist = false)    
private String memberName;    
private String detectionNo;    
/** 指甲类型: strong/soft/brittle/peeling 
*/    
private String nailType;    
/** 综合得分(0-100) 
*/    private BigDecimal overallScore;    
/** 硬度(0-100) 
*/    private BigDecimal hardnessScore;    
/** 光泽度(0-100) 
*/    private BigDecimal glossScore;    
/** 含水量(0-100) 
*/    private BigDecimal moistureScore;    
/** 甲周皮肤状态(0-100, 越高越健康) 
*/    private BigDecimal periungualScore;    
/** 表面平整度(0-100) 
*/    private BigDecimal surfaceSmoothness;    
/** 韧性(0-100) 
*/    private BigDecimal toughnessScore;    
/** 甲沟炎风险(0-100) 
*/    private BigDecimal paronychiaRisk;    
/** 真菌感染风险(0-100) 
*/    private BigDecimal fungalRisk;    
/** 纵裂数量 
*/    private Integer verticalCracks;    
/** 横纹数量 
*/    private Integer horizontalRidges;    
/** 倒刺数量 
*/    private Integer hangnailCount;    
/** 断裂历史(近一年) 
*/    private Integer breakHistory;    
/** 频繁做美甲(每月>=1次) 
*/    private Integer frequentManicure;    
/** 凝胶/光疗甲使用频率 
*/    private Integer gelManicureCount;    
/** 主诉问题JSON 
*/    
private String mainConcerns;    
/** 诊断建议 
*/    
private String diagnosisAdvice;    
private String detectionDevice;    
private String detectionImages;    private Long checkStoreId;    private Long checkEmployeeId;    
private String checkEmployeeName;    private LocalDate detectionDate;    private LocalDate nextCheckDate;    
private String status;    
private String remark;    
@TableField(fill = FieldFill.INSERT)    private LocalDateTime createTime;    
@TableField(fill = FieldFill.INSERT_UPDATE)    private LocalDateTime updateTime;    @TableLogic    private Integer deleted;}
