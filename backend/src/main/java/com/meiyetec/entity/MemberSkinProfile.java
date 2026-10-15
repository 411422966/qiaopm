package com.meiyetec.entity;
import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;
/**
 * 客户肌肤档案
 * 护理建议生成所需的关键输入数据 
*/
@Data
@TableName("biz_member_skin_profile")
public class MemberSkinProfile {    
@TableId(type = IdType.AUTO)    private Long id;    private Long tenantId;    private Long storeId;    private Long memberId;    
private String memberName;    
/** 肤质类型: dry(干性)/oily(油性)/combination(混合性)/sensitive(敏感性)/normal(中性) 
*/    
private String skinType;    
/** 肌肤问题JSON: ['acne','pigmentation','aging','dryness','sensitivity'] 
*/    
private String skinConcerns;    
/** 肌肤敏感度评分 1-10 
*/    private Integer sensitivityScore;    
/** 水分含量 
*/    private Integer moistureScore;    
/** 油脂含量 
*/    private Integer oilScore;    
/** 色素沉着评分 
*/    private Integer pigmentationScore;    
/** 皱纹评分 
*/    private Integer wrinkleScore;    
/** 毛孔评分 
*/    private Integer poreScore;    
/** 最后检测时间 
*/    private LocalDateTime lastCheckTime;    
/** 检测门店ID 
*/    private Long checkStoreId;    
/** 检测设备 
*/    
private String checkDevice;    
/** 检测图片JSON 
*/    
private String checkImages;    
/** 备注 
*/    
private String remark;    
@TableField(fill = FieldFill.INSERT)    private LocalDateTime createTime;    
@TableField(fill = FieldFill.INSERT_UPDATE)    private LocalDateTime updateTime;    @TableLogic    private Integer deleted;}
