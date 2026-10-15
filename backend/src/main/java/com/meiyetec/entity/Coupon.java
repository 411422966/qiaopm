package com.meiyetec.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("market_coupon")
public class Coupon {
    @TableId(type = IdType.AUTO)
    private Long id;
    
    private Long storeId;
    private Long tenantId;
    private String name;
    private Integer type;        // 1满减券 2折扣券 3礼品券
    private Integer ruleType;   // 1满X元减Y 2折扣X% 3无门槛
    private BigDecimal ruleValue;
    private BigDecimal minAmount;
    private BigDecimal maxDiscount;
    private Integer totalCount;
    private Integer perLimit;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private Integer status;     // 0禁用 1启用
    private String remark;
    
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
