package com.meiyetec.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("aggregate_root")
public class AggregateRoot {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String contextCode;
    private String aggregateCode;
    private String aggregateName;
    private String description;
    private String rootTable;
    private String rootIdField;
    private Integer invariantCount;
    private Integer eventCount;
    private String version;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}