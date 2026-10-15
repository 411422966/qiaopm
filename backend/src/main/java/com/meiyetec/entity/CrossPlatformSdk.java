package com.meiyetec.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("cross_platform_sdk")
public class CrossPlatformSdk {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String platformCode;
    private String sdkVersion;
    private String downloadUrl;
    private String fileHash;
    private Long fileSize;
    private String changelog;
    private Integer forceUpgrade;
    private String minClient;
    private Integer deprecated;
    private LocalDateTime releasedAt;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}