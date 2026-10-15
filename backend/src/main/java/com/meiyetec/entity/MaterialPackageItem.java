package com.meiyetec.entity;
import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;
/**
 * 材料包明细
 * 材料包内含的具体材料 
*/
@Data
@TableName("biz_material_package_item")
public class MaterialPackageItem {    
@TableId(type = IdType.AUTO)    private Long id;    private Long tenantId;    
/** 材料包ID 
*/    private Long packageId;    
/** 耗材ID 
*/    private Long productId;    
/** 耗材名称 
*/    
private String productName;    
/** 耗材规格 
*/    
private String spec;    
/** 数量 
*/    private BigDecimal quantity;    
/** 单位(瓶/支/片等) 
*/    
private String unit;    
/** 单价 
*/    private BigDecimal unitPrice;    
/** 小计 
*/    private BigDecimal subtotal;    
/** 是否可独立购买: 0-否, 1-是 
*/    private Integer individually;    
/** 排序 
*/    private Integer sortOrder;    
@TableField(fill = FieldFill.INSERT)    private LocalDateTime createTime;    
@TableField(fill = FieldFill.INSERT_UPDATE)    private LocalDateTime updateTime;    @TableLogic    private Integer deleted;}
