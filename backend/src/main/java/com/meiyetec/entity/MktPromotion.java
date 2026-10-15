package com.meiyetec.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("mkt_promotion")
public class MktPromotion {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private Long storeId;
    private String promotionCode;
    private String promotionName;
    /** FULL_REDUCE/DISCOUNT/GIFT/CASH_COUPON/POINTS_EXCHANGE */
    private String promotionType;
    private String description;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private BigDecimal thresholdAmount;
    private BigDecimal reduceAmount;
    private BigDecimal discountRate;
    private String scopeType;
    private String scopeIds;
    private String memberLevelIds;
    private Integer stackable;
    private String exclusiveCodes;
    private Integer quotaTotal;
    private Integer quotaUsed;
    private Integer perMemberLimit;
    private Integer status;
    private Integer priority;
    private Long operatorId;
    private String operatorName;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
    @TableLogic
    private Integer deleted;
}
