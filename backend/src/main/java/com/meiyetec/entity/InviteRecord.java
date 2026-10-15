package com.meiyetec.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * M09-02 邀请记录
 *
 * 状态机：
 *   1 PENDING    待注册（仅点击链接）
 *   2 REGISTERED 已注册
 *   3 FIRST_ORDER 已首单（达到最低消费门槛）
 *   4 REWARDED   已发放奖励
 *   5 FAILED     已失效（风控拒绝 / 超配额）
 *   6 REJECTED   已拒绝（同人 / 黑名单）
 */
@Data
@TableName("m09_invite_record")
public class InviteRecord {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long tenantId;

    /** 活动ID */
    private Long campaignId;
    private String campaignName;

    /** 邀请码（冗余便于查询） */
    private String inviteCode;

    private Long inviterMemberId;
    private String inviterName;

    private Long inviteeMemberId;
    /** 受邀人手机号（脱敏存储） */
    private String inviteePhone;
    private String inviteeName;

    /** 渠道：WECHAT / SMS / LINK / QR / POSTER */
    private String channel;

    /** 1-6 见类注释 */
    private Integer status;

    private LocalDateTime clickedAt;
    private LocalDateTime registerAt;
    private LocalDateTime firstConsumeAt;
    private Long firstOrderId;
    private LocalDateTime inviterRewardedAt;
    private LocalDateTime inviteeRewardedAt;

    private Long inviterRewardLogId;
    private Long inviteeRewardLogId;

    private String ip;
    private String ua;
    private String deviceId;

    private String failReason;
    private String remark;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    @TableLogic
    private Integer deleted;
}
