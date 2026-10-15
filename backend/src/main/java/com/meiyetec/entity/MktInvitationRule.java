package com.meiyetec.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;
import java.math.BigDecimal;

/**
 * R3.0-E P2：裂变规则配置
 */
@Data
@TableName("mkt_invitation_rule")
public class MktInvitationRule {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private Long brandId;
    private String ruleCode;
    private String ruleName;
    private Integer enabled;
    /** CASH/COUPON/POINTS/NONE */
    private String inviterRewardType;
    private BigDecimal inviterRewardValue;
    /** CASH/COUPON/POINTS/NONE */
    private String inviteeRewardType;
    private BigDecimal inviteeRewardValue;
    private Long inviteeRewardCouponId;
    private BigDecimal minOrderAmount;
    private Integer requireFirstOrder;
    private Integer dailyLimitPerInviter;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private Long operatorId;
    private String operatorName;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
    @TableLogic
    private Integer deleted;
}