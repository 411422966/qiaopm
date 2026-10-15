package com.meiyetec.entity;
import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;
/**
 * 款式包关联
 * 材料包与款式库的多对多关联 
*/
@Data
@TableName("biz_package_style_relation")
public class PackageStyleRelation {    
@TableId(type = IdType.AUTO)    private Long id;    private Long tenantId;    
/** 材料包ID 
*/    private Long packageId;    
/** 款式ID 
*/    private Long styleId;    
/** 款式名称(冗余) 
*/    
private String styleName;    
/** 款式主图(冗余) 
*/    
private String styleImage;    
/** 难度等级 
*/    private Integer difficultyLevel;    
/** 预计耗时 
*/    private Integer durationMinutes;    
/** 主推顺序 
*/    private Integer priority;    
/** 售价 
*/    private BigDecimal stylePrice;    
/** 配套单品ID(JSON): ["productId1","productId2"] 
*/    
private String requiredProducts;    
@TableField(fill = FieldFill.INSERT)    private LocalDateTime createTime;    
@TableField(fill = FieldFill.INSERT_UPDATE)    private LocalDateTime updateTime;    @TableLogic    private Integer deleted;}
