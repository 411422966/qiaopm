package com.meiyetec.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * R3.0-G P3-1：微信小程序配置（appid/secret/支付/模板）
 */
@Data
@TableName("wx_app_config")
public class WxAppConfig {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private Long brandId;
    private String appid;
    private String appName;
    private String appSecret;
    private String mchId;
    private String mchKey;
    private String templateIds;
    private Integer enabled;
    private String operatorName;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
    @TableLogic
    private Integer deleted;
}