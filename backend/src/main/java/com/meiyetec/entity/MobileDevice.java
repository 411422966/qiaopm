package com.meiyetec.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("mobile_device")
public class MobileDevice {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private Long userId;
    private String deviceId;
    /** iOS / Android / MiniProgram / H5 */
    private String platform;
    private String pushToken;
    private String appVersion;
    private String osVersion;
    private String deviceModel;
    private LocalDateTime lastLoginAt;
    private Integer loginCount;
    private Integer enabled;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}