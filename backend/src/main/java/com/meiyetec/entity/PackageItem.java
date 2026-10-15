package com.meiyetec.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("crm_package")
public class PackageItem {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long tenantId;
    private String name;
    private String code;
    private String category;
    private String cover;
    private String description;
    private Integer totalTimes;
    private BigDecimal price;
    private BigDecimal costPrice;
    private Integer validDays;
    private Integer saleCount;
    private Integer redeemCount;
    private Integer status;
    private String remark;
    private Long createBy;

    /** M04 bundle: 0=单商品 1=组合套餐（含多个子项目） */
    private Integer bundleType;
    /** M04 bundle: 子项目快照 (JSON: [{serviceId, name, times, unitPrice}, ...]) */
    private String bundleConfig;
    /** M04 bundle: 子项目总数（冗余用于快速展示） */
    private Integer bundleItemCount;

    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    /** 状态描述（不存库） */
    @TableField(exist = false)
    private String statusLabel;

    /** M04 bundle: 解析后的子项目列表（不存库） */
    @TableField(exist = false)
    private java.util.List<BundleSubItem> bundleItems;
}
