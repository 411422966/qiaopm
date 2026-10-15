package com.meiyetec.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("dianping_deal")
public class DianpingDeal {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private String shopId;
    private String dealId;
    private String dealName;
    private String category;
    private BigDecimal price;
    private BigDecimal originalPrice;
    private Integer stock;
    private Integer soldCount;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private String status;
    private Long localServiceId;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}