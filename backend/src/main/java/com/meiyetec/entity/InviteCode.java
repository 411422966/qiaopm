package com.meiyetec.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * M09-02 邀请码
 *
 * 每位会员在每个邀请活动中拥有唯一邀请码。
 * uk_campaign_code: (campaign_id, invite_code) 唯一
 * uk_campaign_member: (campaign_id, member_id) 唯一
 */
@Data
@TableName("m09_invite_code")
public class InviteCode {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long tenantId;
    private Long campaignId;
    private Long memberId;
    private String memberName;
    private String memberPhone;

    /** 活动内唯一邀请码（8-12 位字母数字） */
    private String inviteCode;

    /** 短链（可空） */
    private String shortLink;

    // ===== 漏斗统计 =====
    private Integer shareCount;
    private Integer scanCount;
    private Integer registerCount;
    private Integer successCount;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    @TableLogic
    private Integer deleted;
}
