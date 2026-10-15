package com.meiyetec.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("platform_sync_log")
public class PlatformSyncLog {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    /** MEITUAN/DOUYIN */
    private String platform;
    /** ORDER_PULL / BOOKING_PULL / STATUS_PUSH */
    private String syncType;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private Integer total;
    private Integer success;
    private Integer fail;
    private String detail;
    private String operatorName;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}