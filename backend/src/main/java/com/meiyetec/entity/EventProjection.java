package com.meiyetec.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("event_projection")
public class EventProjection {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String projectionName;
    private String aggregateType;
    /** 已处理的最后序号 */
    private Long lastSequence;
    private Long eventCount;
    private LocalDateTime lastEventAt;
    /** RUNNING / PAUSED / FAILED */
    private String status;
    private String errorMessage;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}