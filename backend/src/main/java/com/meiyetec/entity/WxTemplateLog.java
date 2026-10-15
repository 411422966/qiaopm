package com.meiyetec.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("wx_template_log")
public class WxTemplateLog {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private String appid;
    private String openid;
    private String templateId;
    private String templateData;
    private String msgId;
    /** PENDING/SUCCESS/FAIL */
    private String status;
    private String errCode;
    private String errMsg;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime sendAt;
}