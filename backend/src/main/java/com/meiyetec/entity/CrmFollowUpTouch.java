package com.meiyetec.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * R3.0-H P0.2：回访触达日志（电话/短信/微信/...）
 */
@Data
@TableName("crm_follow_up_touch")
public class CrmFollowUpTouch {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private Long taskId;
    /** PHONE/SMS/WECHAT/WX_WORK/EMAIL/FACE */
    private String touchType;
    /** SENT/REPLIED/FAILED/NO_ANSWER */
    private String touchStatus;
    private String contact;
    private String content;
    private String replyContent;
    private LocalDateTime replyAt;
    private Integer durationSeconds;
    private Long operatorId;
    private String operatorName;
    private String remark;
    private LocalDateTime touchedAt;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}