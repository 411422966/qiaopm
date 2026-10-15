package com.meiyetec.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * CRM 同步日志
 *
 * <p>EXT-15 扩展：每次同步保存 syncToken / recordsTotal / recordsFiltered，
 * 用于回溯与对账。
 */
@Data
@TableName("crm_sync_log")
public class CrmSyncLog {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private String platform;
    /** INCREMENTAL / FULL / MANUAL */
    private String syncType;
    /** PUSH / PULL / BIDIRECTIONAL */
    private String direction;
    private Integer successCount;
    private Integer failCount;
    private Integer skippedCount;
    private String errorMessage;
    private Integer durationMs;
    /** RUNNING / SUCCESS / FAILED */
    private String status;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    // ============= EXT-15：增量同步游标与计数 =============
    /** 平台 sync token（Salesforce 用，记录本次同步结束时的游标） */
    private String syncToken;
    /** 平台侧返回的总记录数 */
    private Integer recordsTotal;
    /** 经去重 / 过滤后的待处理数 */
    private Integer recordsFiltered;
}
