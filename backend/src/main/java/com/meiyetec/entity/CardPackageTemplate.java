package com.meiyetec.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("card_package_template")
public class CardPackageTemplate {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long tenantId;
    private String templateCode;
    private String templateName;
    private String packageType;
    private String cardLevel;

    private Integer totalSessions;
    private BigDecimal totalAmount;
    private BigDecimal originalPrice;
    private BigDecimal packagePrice;
    private BigDecimal discountRate;

    private String coverImage;
    private String themeColor;
    private String accentColor;
    private String description;
    private String highlights;
    private String tag;

    private String storeIds;

    private String includedServices;
    private String includedProducts;

    private String status;
    private Integer soldCount;
    private Integer viewCount;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}