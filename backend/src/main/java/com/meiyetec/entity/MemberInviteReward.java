package com.meiyetec.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * P4-02 · QPM-006: 会员裂变奖励记录
 *
 * 记录每一对邀请人与被邀请人的奖励发放情况。
 * 双方奖励：邀请人（COUPON/POINTS/CASH）+ 被邀请人（首单奖励）。
 */
@Data
@TableName("member_invite_reward")
public class MemberInviteReward {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long tenantId;
    private Long inviteCodeId;

    private Long inviterId;
    private Long inviteeId;
    private String inviteeName;
    private String inviteePhone;

    private LocalDateTime registerAt;

    private Long firstOrderId;
    private LocalDateTime firstOrderAt;

    /** COUPON / POINTS / CASH */
    private String inviterRewardType;
    private String inviterRewardValue;

    /** PENDING / SENT / USED */
    private String inviterRewardStatus;

    private String inviteeRewardType;
    private String inviteeRewardValue;
    private String inviteeRewardStatus;

    private String operatorName;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    @TableLogic
    private Integer deleted;
}