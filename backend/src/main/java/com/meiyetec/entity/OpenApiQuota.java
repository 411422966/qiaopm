package com.meiyetec.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("open_api_quota")
public class OpenApiQuota {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private Long appId;
    /** MINUTE / HOUR / DAY / MONTH */
    private String window;
    private Integer quotaLimit;
    private Integer usedCount;
    private LocalDateTime windowStart;
    private LocalDateTime windowEnd;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}