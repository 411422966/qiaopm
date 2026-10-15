package com.meiyetec.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * P4-02 · QPM-006: 会员裂变邀请码
 *
 * 每个会员最多一个 ACTIVE 邀请码。
 * 通过 INV{8位随机} 生成，uk_invite_code 保证全局唯一。
 */
@Data
@TableName("member_invite_code")
public class MemberInviteCode {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long tenantId;
    private Long inviterId;

    /** 邀请码 INV + 8位 */
    private String inviteCode;

    /** 分享渠道：WX / QR / LINK / SMS */
    private String inviteChannel;

    private Integer inviteCount;
    private Integer successCount;

    private Long rewardCouponTemplate;
    private BigDecimal rewardCouponAmount;

    private LocalDateTime expireAt;

    /** ACTIVE / EXPIRED / DISABLED */
    private String status;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    @TableLogic
    private Integer deleted;
}