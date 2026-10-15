package com.meiyetec.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("feature_flag")
public class FeatureFlag {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private String flagKey;
    private String flagName;
    private String description;
    /** 总开关 */
    private Integer enabled;
    /** 灰度百分比 0-100 */
    private Integer rolloutPercent;
    private String whitelist;
    private String blacklist;
    /** USER_ID / STORE_ID / REGION / HASH */
    private String userRule;
    private LocalDateTime startAt;
    private LocalDateTime endAt;
    private String owner;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}