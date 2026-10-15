package com.meiyetec.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("crm_cross_store_redemption")
public class CrmCrossStoreRedemption {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private Long brandId;
    private Long orderId;
    private String orderNo;
    private Long memberId;
    private String memberName;
    private String memberPhone;
    private Long sourceStoreId;
    private Long targetStoreId;
    /** PACKAGE/PROJECT/BALANCE/POINTS/COUPON/DISCOUNT */
    private String redemptionType;
    private Long sourceId;
    private String sourceName;
    private BigDecimal originalAmount;
    private BigDecimal crossFee;
    private BigDecimal netAmount;
    /** 1已核销 2已撤销 */
    private Integer status;
    private Long ruleId;
    private String ruleSnapshot;
    private Long operatorId;
    private String operatorName;
    private String remark;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
    @TableLogic
    private Integer deleted;
}
