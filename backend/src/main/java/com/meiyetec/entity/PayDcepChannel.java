package com.meiyetec.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("pay_dcep_channel")
public class PayDcepChannel {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private Long brandId;
    /** DCEP-DCBC/DCEP-ICBC/DCEP-ABC */
    private String channelCode;
    private String channelName;
    private String merchantId;
    private String appId;
    private String appSecret;
    private String privateKeyPath;
    private String publicKeyPath;
    private String apiBaseUrl;
    private String notifyUrl;
    /** SANDBOX/MOCK/PROD */
    private String envMode;
    private Integer status;
    private LocalDateTime lastHealthAt;
    private String lastHealthMsg;
    private Integer rateLimitPerMin;
    private Long operatorId;
    private String operatorName;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
    @TableLogic
    private Integer deleted;
}