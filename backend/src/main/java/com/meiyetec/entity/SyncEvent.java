package com.meiyetec.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("sync_event")
public class SyncEvent {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String sessionKey;
    private String fromDeviceId;
    /** NAV / FOCUS / STATE_CHANGE / ACTION */
    private String eventType;
    private String entityType;
    private String entityId;
    private String payload;
    private LocalDateTime occurredAt;
}