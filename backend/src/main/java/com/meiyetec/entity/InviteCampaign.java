package com.meiyetec.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * M09-02 裂变邀请：活动配置
 *
 * 一个活动代表一个"邀请有礼"方案；同一租户可并行多个活动。
 * 奖励类型：1=优惠券 2=积分 3=储值 4=自定义
 * 状态：1=启用 0=停用
 */
@Data
@TableName("m09_invite_campaign")
public class InviteCampaign {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 租户ID */
    private Long tenantId;

    /** 活动编码（租户内唯一） */
    private String campaignCode;

    /** 活动名称 */
    private String campaignName;

    /** 活动类型：INVITE_REWARD / GROUP_BUY */
    private String campaignType;

    // ===== 邀请人奖励 =====
    /** 1优惠券 2积分 3储值 4自定义 */
    private Integer inviterRewardType;
    private BigDecimal inviterRewardValue;
    /** 奖励元数据 JSON（couponId / pointsAmount / walletAmount） */
    private String inviterRewardMeta;

    // ===== 新人奖励 =====
    private Integer inviteeRewardType;
    private BigDecimal inviteeRewardValue;
    private String inviteeRewardMeta;

    // ===== 触发门槛 =====
    /** 新人首单最低消费（元），达此门槛才发放双方奖励 */
    private BigDecimal inviteeMinConsume;

    /** 是否需要注册（0=仅点击即可 1=必须注册） */
    private Boolean inviteNeedRegister;

    // ===== 配额 =====
    /** 单人每日邀请上限 */
    private Integer inviterQuotaPerDay;
    /** 单人累计邀请上限 */
    private Integer inviterQuotaTotal;
    /** 活动总配额（0=不限） */
    private Integer campaignQuotaTotal;

    // ===== 分享文案 =====
    private String shareTitle;
    private String shareDesc;
    private String shareImageUrl;
    private String shareLandingUrl;

    // ===== 时间与状态 =====
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    /** 1启用 0停用 */
    private Integer status;

    // ===== 审计 =====
    private Long operatorId;
    private String operatorName;
    private String remark;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    @TableLogic
    private Integer deleted;
}
