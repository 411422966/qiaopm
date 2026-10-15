package com.meiyetec.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("price_list")
public class PriceList {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long tenantId;
    private String listCode;
    private String listName;
    private String listType;
    private Long storeId;
    private String memberLevel;
    private LocalDate effectiveDate;
    private LocalDate expireDate;

    private String status;
    private Integer isDefault;
    private Integer version;

    private String description;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}