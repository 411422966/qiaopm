package com.meiyetec.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("push_record")
public class PushRecord {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private String templateCode;
    private Long userId;
    private String deviceId;
    private String platformCode;
    private String channel;
    private String title;
    private String content;
    /** PENDING / SENT / FAILED / READ */
    private String status;
    private String errorMessage;
    private LocalDateTime sentAt;
    private LocalDateTime readAt;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}