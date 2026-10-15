package com.meiyetec.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * M07 服务/产品实体
 */
@Data
@TableName("biz_service")
public class BizService {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private Long storeId;
    private String serviceCode;
    private String serviceName;
    private Long categoryId;
    private BigDecimal unitPrice;
    private BigDecimal memberPrice;
    private String unit;
    private Integer duration;
    private String description;
    private String imageUrl;
    private Integer isPackage;
    private Integer status;
    private Integer sortOrder;

    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    @TableLogic
    private Integer deleted;

    @TableField(exist = false)
    private String categoryName;
}
