package com.meiyetec.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("monitor_notification_log")
public class MonitorNotificationLog {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long alertEventId;
    /** SMS / EMAIL / WECHAT / WEBHOOK / DINGTALK */
    private String channel;
    private String recipient;
    private String subject;
    private String content;
    /** PENDING / SUCCESS / FAILED */
    private String status;
    private String errorMessage;
    private LocalDateTime sentAt;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}