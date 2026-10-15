package com.meiyetec.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDate;

@Data
@TableName("brand_store_rel")
public class BrandStoreRel {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long brandId;
    private Long storeId;
    private String storeCode;
    private LocalDate joinAt;
    private LocalDate leaveAt;
    private Integer isFlagship;
    private Integer isActive;
}