package com.meiyetec.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("price_list_item")
public class PriceListItem {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long priceListId;
    private Long serviceId;
    private Long categoryId;

    private BigDecimal listPrice;
    private BigDecimal memberPrice;
    private BigDecimal vipPrice;
    private BigDecimal discountRate;

    private String imageUrl;
    private String tag;
    private Integer sortOrder;
    private Integer isFeatured;
    private Integer soldCount;

    private String remark;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}