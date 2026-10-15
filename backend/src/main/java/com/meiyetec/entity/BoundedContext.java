package com.meiyetec.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("bounded_context")
public class BoundedContext {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String contextCode;
    private String contextName;
    private String description;
    /** CORE / SUPPORTING / GENERIC */
    private String domainLayer;
    private Integer aggregateCount;
    private String upstreamCtx;
    private String downstreamCtx;
    private String teamOwner;
    /** ACTIVE / DEPRECATED / PLANNED */
    private String status;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}