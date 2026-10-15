package com.meiyetec.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * R2.1-A 服务项目实体（新版，比 biz_service 字段更全）
 *  - serviceCode：项目编码（业务唯一）
 *  - categoryId：分类
 *  - unitPrice / memberPrice：标准价 / 会员价
 *  - costPrice：成本价（按 BOM 加权，自动计算）
 *  - duration：服务时长
 *  - sop：操作流程
 */
@Data
@TableName("biz_service_item")
public class BizServiceItem {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private Long storeId;
    private String serviceCode;
    private String serviceName;
    private Long categoryId;
    private BigDecimal unitPrice;
    private BigDecimal memberPrice;
    private BigDecimal costPrice;
    private Integer duration;
    private String unit;
    private String description;
    private String imageUrl;
    private String sop;
    private Integer status;
    private Integer sortOrder;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
    @TableLogic
    private Integer deleted;

    /** 关联展示 */
    @TableField(exist = false)
    private String categoryName;
}
