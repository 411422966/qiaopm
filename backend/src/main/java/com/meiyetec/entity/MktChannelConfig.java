package com.meiyetec.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("mkt_channel_config")
public class MktChannelConfig {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private Long brandId;
    /** SMS/MINI_PROGRAM/MP/WECHAT_WORK/IN_APP/EMAIL/PUSH */
    private String channelType;
    private String channelName;
    private String provider;
    private String configJson;
    private Integer rateLimitPerMin;
    private Integer rateLimitPerDay;
    private String signature;
    private String templatePrefix;
    private Integer status;
    private LocalDateTime lastHealthAt;
    private String lastHealthMsg;
    private Integer retryMax;
    private Integer retryIntervalSec;
    private Long operatorId;
    private String operatorName;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
    @TableLogic
    private Integer deleted;
}
