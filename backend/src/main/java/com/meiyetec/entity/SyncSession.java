package com.meiyetec.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("sync_session")
public class SyncSession {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private String sessionKey;
    private Long userId;
    private Integer deviceCount;
    private LocalDateTime startedAt;
    private LocalDateTime endedAt;
    /** ACTIVE / CLOSED / EXPIRED */
    private String status;
    private LocalDateTime lastEventAt;
    private Integer eventCount;
}