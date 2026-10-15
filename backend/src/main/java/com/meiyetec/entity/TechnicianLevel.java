package com.meiyetec.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 技师等级
 * 技师晋升等级体系，如：初级技师、中级技师、高级技师、首席技师
 */
@Data
@TableName("biz_technician_level")
public class TechnicianLevel {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 等级编码,如: L1/L2/L3/L4 */
    private String levelCode;

    /** 等级名称 */
    private String levelName;

    /** 等级图标URL */
    private String levelIcon;

    /** 等级颜色 #RRGGBB */
    private String levelColor;

    /** 等级排序,数字越小等级越高 */
    private Integer sortOrder;

    /** 服务折扣比例,如: 10% 表示九折 */
    private BigDecimal discountRate;

    /** 提成比例加成,如: 0.05 表示加成5% */
    private BigDecimal commissionBonus;

    /** 晋升要求业绩(元),达到该业绩才能晋升到此等级 */
    private BigDecimal promotionSales;

    /** 晋升要求好评率,0-100 */
    private BigDecimal promotionRating;

    /** 晋升要求最低服务时长(月) */
    private Integer promotionMonths;

    /** 等级特权描述 */
    private String privileges;

    /** 关联项目分类(JSON数组) */
    private String serviceCategories;

    /** 状态: 0-停用, 1-启用 */
    private Integer status;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    @TableLogic
    private Integer deleted;

    @TableField(exist = false)
    private Integer employeeCount;
}
