package com.meiyetec.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("plugin_market")
public class PluginMarket {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private String pluginCode;
    private String pluginName;
    private String category;
    private String version;
    private String vendor;
    private String shortDesc;
    private String description;
    private String iconUrl;
    private String tags;
    private BigDecimal price;
    private String billingType;
    private Integer installCount;
    private BigDecimal rating;
    private Integer reviewCount;
    private Integer isOfficial;
    private Integer isFeatured;
    private String homepage;
    private String tarballUrl;
    private String configJson;
    private String permissions;
    private String events;
    private String status;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}