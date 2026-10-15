package com.meiyetec.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("push_template")
public class PushTemplate {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private String templateCode;
    private String title;
    private String content;
    private String platformCodes;
    /** NOTICE / MARKETING / VERIFY / ALERT */
    private String channel;
    private String deepLink;
    private String iconUrl;
    private String sound;
    private Integer vibration;
    private Integer enabled;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}