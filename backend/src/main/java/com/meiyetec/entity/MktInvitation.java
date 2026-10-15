package com.meiyetec.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;
import java.math.BigDecimal;

/**
 * R3.0-E P2：裂变邀请记录
 */
@Data
@TableName("mkt_invitation")
public class MktInvitation {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private Long inviterId;
    private String inviterName;
    private Long inviteeId;
    private String inviteePhone;
    private String inviteeName;
    private String inviteCode;
    /** LINK/SMS/WECHAT/QRCODE */
    private String channel;
    /** PENDING/REGISTERED/ORDERED/REWARDED */
    private String status;
    private LocalDateTime registeredAt;
    private Long firstOrderId;
    private LocalDateTime firstOrderAt;
    private BigDecimal inviteReward;
    private BigDecimal inviteeReward;
    /** PENDING/SENT/FAILED */
    private String rewardStatus;
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