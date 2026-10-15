package com.meiyetec.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("domain_event_store")
public class DomainEventStore {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String aggregateType;
    private String aggregateId;
    /** 事件 ID（UUID） */
    private String eventId;
    private String eventType;
    private Integer eventVersion;
    /** JSON 载荷 */
    private String payload;
    private LocalDateTime occurredAt;
    private Long tenantId;
    private Long userId;
    private String correlationId;
    private String causationId;
    /** 聚合内序号 */
    private Long sequence;
}