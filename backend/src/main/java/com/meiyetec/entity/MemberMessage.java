package com.meiyetec.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * M09-01：会员站内信
 * 存储 InAppSender 写入的通知消息
 */
@Data
@TableName("member_message")
public class MemberMessage {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 租户ID */
    private Long tenantId;

    /** 会员ID */
    private Long memberId;

    /** 会员姓名 */
    private String memberName;

    /** 消息标题 */
    private String title;

    /** 消息内容 */
    private String content;

    /** SYSTEM / BIRTHDAY / EXPIRE / PROMO / TASK */
    private String messageType;

    /** 来源渠道：IN_APP / SMS / MP */
    private String channelType;

    /** 关联类型：ORDER / PACKAGE / BOOKING */
    private String relatedType;

    /** 关联业务ID */
    private Long relatedId;

    /** 是否已读 */
    private Boolean isRead;

    /** 阅读时间 */
    private LocalDateTime readAt;

    /** 1高 2普通 3低 */
    private Integer priority;

    /** 过期时间 */
    private LocalDateTime expireAt;

    private Long operatorId;
    private String operatorName;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    @TableLogic
    private Integer deleted;
}
