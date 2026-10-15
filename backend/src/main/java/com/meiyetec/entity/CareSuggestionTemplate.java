package com.meiyetec.entity;
import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;
/**
 * 护理建议模板
 * 配置不同场景的护理建议内容模板，支持变量替换 
*/
@Data
@TableName("biz_care_suggestion_template")
public class CareSuggestionTemplate {    
@TableId(type = IdType.AUTO)    private Long id;    private Long tenantId;    private Long storeId;    
/** 模板名称 
*/    
private String templateName;    
/** 模板编码 
*/    
private String templateCode;    
/** 模板类型: skin(肌肤)/body(身体)/hair(头发)/nail(美甲)/custom(自定义) 
*/    
private String templateType;    
/** 适用人群标签(多个用逗号分隔) 
*/    
private String targetTags;    
/** 建议标题 
*/    
private String title;    
/** 建议内容(支持变量如 {member_name} {skin_type}) 
*/    
private String content;    
/** 推荐服务JSON: [{serviceId, serviceName, reason}] 
*/    
private String recommendedServices;    
/** 推荐产品JSON 
*/    
private String recommendedProducts;    
/** 优先级: 1-5 
*/    private Integer priority;    
/** 状态: 0-停用, 1-启用 
*/    private Integer status;    
@TableField(fill = FieldFill.INSERT)    private LocalDateTime createTime;    
@TableField(fill = FieldFill.INSERT_UPDATE)    private LocalDateTime updateTime;    @TableLogic    private Integer deleted;}
