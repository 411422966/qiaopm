package com.meiyetec.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

@Data
@TableName("meta_column")
public class MetaColumn {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long metaTableId;
    private String columnName;
    private String columnType;
    private String dataType;
    private Integer isPrimaryKey;
    private Integer isNullable;
    private String description;
    private Integer isSensitive;
    private String enumValues;
    private Integer sortOrder;
}