package com.meiyetec.entity;
import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;
/**
 * 客户款式偏好
 * 记录客户对款式的喜好、过往选择 
*/
@Data
@TableName("biz_member_style_preference")
public class MemberStylePreference {    
@TableId(type = IdType.AUTO)    private Long id;    private Long tenantId;    
/** 客户ID 
*/    private Long memberId;    
/** 客户姓名 
*/    
@TableField(exist = false)    
private String memberName;    
/** 偏好款式类型: nail_art/eyelash/hair_style/makeup 
*/    
private String preferredType;    
/** 偏好风格标签JSON 
*/    
private String preferredStyles;    
/** 偏好色系JSON 
*/    
private String preferredColors;    
/** 忌讳色系JSON 
*/    
private String avoidColors;    
/** 偏好长度: short/medium/long 
*/    
private String preferredLength;    
/** 偏好形状: round/square/oval/almond/coffin 
*/    
private String preferredShape;    
/** 过敏成分JSON 
*/    
private String allergyIngredients;    
/** 偏好技师ID 
*/    private Long preferredTechnicianId;    
/** 偏好技师姓名 
*/    
private String preferredTechnicianName;    
/** 特殊备注 
*/    
private String specialNote;    
/** 最近浏览款式JSON(最多10个) 
*/    
private String recentStyles;    
/** 收藏款式JSON 
*/    
private String favoriteStyles;    
/** 历史订单款式JSON 
*/    
private String historyStyles;    
/** AI推荐次数 
*/    private Integer recommendCount;    
private String remark;    
@TableField(fill = FieldFill.INSERT)    private LocalDateTime createTime;    
@TableField(fill = FieldFill.INSERT_UPDATE)    private LocalDateTime updateTime;    @TableLogic    private Integer deleted;}
