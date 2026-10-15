package com.meiyetec.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("dr_site")
public class DrSite {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String siteCode;
    private String siteName;
    /** PRIMARY / STANDBY / DISASTER */
    private String siteType;
    private String region;
    private String zone;
    private String endpoint;
    /** ACTIVE / STANDBY / OFFLINE */
    private String role;
    private Integer priority;
    /** 数据丢失容忍 */
    private Integer rpoSeconds;
    /** 恢复时间目标 */
    private Integer rtoSeconds;
    private LocalDateTime lastHeartbeat;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}