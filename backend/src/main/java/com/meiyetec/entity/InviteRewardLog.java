package com.meiyetec.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * M09-02 邀请奖励发放日志（不可变）
 *
 * 每条奖励发放无论成功失败都会写入，用于：
 *   1. 财务对账（inviter / invitee 双方奖励明细）
 *   2. 风控审计
 *   3. 失败重试查询
 */
@Data
@TableName("m09_invite_reward_log")
public class InviteRewardLog {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long tenantId;
    private Long recordId;
    private Long campaignId;
    private String campaignName;

    /** 1 邀请人 2 受邀人 */
    private Integer rewardTo;

    private Long memberId;
    private String memberName;
    private String memberPhone;

    /** 1优惠券 2积分 3储值 */
    private Integer rewardType;
    private String rewardTypeText;
    private BigDecimal rewardValue;
    private String rewardName;

    /** 关联实体 */
    private Long couponGrantId;
    private Long pointsRecordId;
    private Long walletTxnId;

    /** 1成功 2失败 3已回滚 */
    private Integer status;
    private String failReason;
    private String errorCode;

    private Long operatorId;
    private String operatorName;
    /** 是否系统自动发放 */
    private Boolean isAuto;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    /** 日志不可更新：仅 INSERT 阶段填充 */
    @TableField(exist = false)
    private LocalDateTime updatedAt;
}
