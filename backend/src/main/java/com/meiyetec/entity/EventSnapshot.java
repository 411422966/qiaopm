package com.meiyetec.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("event_snapshot")
public class EventSnapshot {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String aggregateType;
    private String aggregateId;
    /** 基于 sequence 的快照 */
    private Long sequence;
    /** JSON 状态 */
    private String state;
    private String version;
    private LocalDateTime createdAt;
}