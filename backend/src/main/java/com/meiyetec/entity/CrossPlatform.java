package com.meiyetec.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("cross_platform")
public class CrossPlatform {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String platformCode;
    private String platformName;
    /** WEB / H5 / MP / APP / PAD */
    private String platformType;
    private String framework;
    private String minVersion;
    private String sdkVersion;
    private Integer enabled;
    private String iconUrl;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}