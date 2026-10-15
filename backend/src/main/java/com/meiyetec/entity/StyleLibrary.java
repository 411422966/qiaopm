package com.meiyetec.entity;
import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;
/**
 * 款式库
 * 美甲/美睫/发型等款式作品库 
*/
@Data
@TableName("biz_style_library")
public class StyleLibrary {    
@TableId(type = IdType.AUTO)    private Long id;    private Long tenantId;    private Long storeId;    
/** 款式编号 
*/    
private String styleCode;    
/** 款式名称 
*/    
private String styleName;    
/** 款式类型: nail_art(美甲)/eyelash(美睫)/hair_style(发型)/makeup(妆容) 
*/    
private String styleType;    
/** 风格标签: 简约/复古/甜美/ins风/OL/中式 等 
*/    
private String styleTag;    
/** 主色系JSON: ["红色","粉色"] 
*/    
private String colorScheme;    
/** 难度等级: 1-5 
*/    private Integer difficultyLevel;    
/** 预计耗时(分钟) 
*/    private Integer durationMinutes;    
/** 基础价格 
*/    private java.math.BigDecimal basePrice;    
/** VIP价格 
*/    private java.math.BigDecimal vipPrice;    
/** 主图URL 
*/    
private String mainImage;    
/** 详情图JSON数组 
*/    
private String detailImages;    
/** 视频URL 
*/    
private String videoUrl;    
/** 款式描述 
*/    
private String description;    
/** 操作步骤(文字描述) 
*/    
private String operationSteps;    
/** 关键工具JSON 
*/    
private String requiredTools;    
/** 技师擅长: 高级/资深/专家 
*/    
private String technicianLevel;    
/** 浏览数 
*/    private Integer viewCount;    
/** 收藏数 
*/    private Integer favoriteCount;    
/** 使用次数 
*/    private Integer useCount;    
/** 评分(0-5) 
*/    private java.math.BigDecimal ratingScore;    
/** 状态: 0-下架, 1-上架 
*/    private Integer status;    
/** 是否新品: 0-否, 1-是 
*/    private Integer isNew;    
/** 是否热门: 0-否, 1-是 
*/    private Integer isHot;    
/** 发布人ID 
*/    private Long publishEmployeeId;    
/** 发布人姓名 
*/    
private String publishEmployeeName;    
/** 发布时间 
*/    private LocalDateTime publishTime;    
private String remark;    
@TableField(fill = FieldFill.INSERT)    private LocalDateTime createTime;    
@TableField(fill = FieldFill.INSERT_UPDATE)    private LocalDateTime updateTime;    @TableLogic    private Integer deleted;}
