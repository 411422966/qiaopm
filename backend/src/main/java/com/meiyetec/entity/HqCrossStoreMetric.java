package com.meiyetec.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;

@Data
@TableName("hq_cross_store_metric")
public class HqCrossStoreMetric {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private Long brandId;
    /** DAY / WEEK / MONTH */
    private String periodType;
    private String periodKey;
    private Integer totalStores;
    private Integer activeStores;
    private BigDecimal totalRevenue;
    private Long totalOrders;
    private Long totalMembers;
    private Long newMembers;
    private BigDecimal avgOrderAmt;
}