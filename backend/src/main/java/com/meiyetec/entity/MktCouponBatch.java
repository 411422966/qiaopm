package com.meiyetec.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("mkt_coupon_batch")
public class MktCouponBatch {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private Long storeId;
    private String batchNo;
    private Long promotionId;
    private String couponName;
    /** CASH/PERCENT/POINTS */
    private String couponType;
    private BigDecimal discountAmount;
    private BigDecimal discountRate;
    private BigDecimal minConsume;
    private String discountScope;
    private Integer totalCount;
    private Integer issuedCount;
    private Integer usedCount;
    private Integer effectiveDays;
    private LocalDate fixedStartDate;
    private LocalDate fixedEndDate;
    private String grantType;
    /** 1草稿 2发放中 3已结束 4取消 */
    private Integer status;
    private Integer expireWarningDays;
    private Long operatorId;
    private String operatorName;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
    @TableLogic
    private Integer deleted;
}
