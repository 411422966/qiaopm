package com.meiyetec.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("cqrs_query_model")
public class CqrsQueryModel {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String modelName;
    private String sourceAggregate;
    private String description;
    /** 建表 SQL */
    private String schemaSql;
    private LocalDateTime lastRefreshedAt;
    private Integer refreshIntervalSec;
    private Integer enabled;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}