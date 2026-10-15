package com.meiyetec.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * CRM Contact 双向 ID 映射
 *
 * <p>EXT-15：本地 Contact/Member ID ↔ 平台侧 ID（Salesforce Contact Id 等）。
 * 双向同步时通过此表反查"哪条本地数据对应哪个平台 ID"。
 */
@Data
@TableName("crm_contact_map")
public class CrmContactMap {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    /** SALESFORCE / HUBSPOT / ... */
    private String platform;
    /** 本系统 Contact/Member ID */
    private Long localContactId;
    /** 平台侧 ID（Salesforce Contact Id，18 位 / 15 位） */
    private String externalId;
    /** 乐观锁 / 平台 revision marker */
    private String externalEtag;
    /** INBOUND / OUTBOUND / BIDIRECTIONAL */
    private String syncDirection;
    private LocalDateTime lastInboundAt;
    private LocalDateTime lastOutboundAt;
    private LocalDateTime lastFullSyncAt;
    /** 0=无冲突 1=有冲突待人工处理 */
    private Integer conflictFlag;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
