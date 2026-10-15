package com.meiyetec.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("marketing_touch_log")
public class MarketingTouchLog {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private Long taskId;
    private Long memberId;
    private String memberName;
    private String memberPhone;
    private String channelType;
    private String content;
    /** 1待发送 2已发送 3失败 4已读 5已转化 */
    private Integer status;
    private String errorMsg;
    private LocalDateTime scheduledAt;
    private LocalDateTime sentAt;
    private LocalDateTime readAt;
    private LocalDateTime convertAt;
    private Long relatedOrderId;
    private Integer retryCount;
    private String externalMsgId;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
    @TableLogic
    private Integer deleted;
}
