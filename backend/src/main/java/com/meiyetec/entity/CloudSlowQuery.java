package com.meiyetec.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("cloud_slow_query")
public class CloudSlowQuery {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private String sqlText;
    private String sqlHash;
    private Long executionMs;
    private Long rowsExamined;
    private Long rowsSent;
    private String databaseName;
    private String tableName;
    private String userName;
    private String traceId;
    private String endpoint;
    private LocalDateTime occurredAt;
    private String suggestion;
    private Integer indexed;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}