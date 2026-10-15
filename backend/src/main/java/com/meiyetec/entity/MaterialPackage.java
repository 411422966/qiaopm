package com.meiyetec.entity;
import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;
/**
 * 材料包
 * 一组材料的集合，可用于快速补货 
*/
@Data
@TableName("biz_material_package")
public class MaterialPackage {    
@TableId(type = IdType.AUTO)    private Long id;    private Long tenantId;    private Long storeId;    
/** 材料包编号 
*/    
private String packageCode;    
/** 材料包名称 
*/    
private String packageName;    
/** 包类型: starter(开业包)/kit(套装)/combo(组合装)/seasonal(季节包) 
*/    
private String packageType;    
/** 包用途描述 
*/    
private String purpose;    
/** 主图URL 
*/    
private String mainImage;    
/** 总成本 
*/    private BigDecimal totalCost;    
/** 售价 
*/    private BigDecimal salePrice;    
/** 折扣(0-1,默认0.9) 
*/    private BigDecimal discountRate;    
/** 实际售价 = 售价
 * 折扣 
*/    private BigDecimal finalPrice;    
/** 包含款式数(用于款式包时) 
*/    private Integer styleCount;    
/** 预计可做款式数(用于款式包时) 
*/    private Integer serveCount;    
/** 适用对象 
*/    
private String applyScope;    
/** 标签 
*/    
private String tags;    
/** 关联活动ID 
*/    private Long activityId;    
/** 是否上架 
*/    private Integer status;    
/** 销量 
*/    private Integer salesCount;    
/** 库存数量(包) 
*/    private Integer stockQuantity;    
/** 创建人ID 
*/    private Long creatorId;    
/** 创建人姓名 
*/    
private String creatorName;    
/** 发布日期 
*/    private LocalDateTime publishTime;    
private String remark;    
@TableField(fill = FieldFill.INSERT)    private LocalDateTime createTime;    
@TableField(fill = FieldFill.INSERT_UPDATE)    private LocalDateTime updateTime;    @TableLogic    private Integer deleted;}
