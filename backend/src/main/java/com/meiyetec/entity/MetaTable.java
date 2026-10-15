package com.meiyetec.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("meta_table")
public class MetaTable {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private String tableCode;
    private String tableName;
    private String dbName;
    private String schemaName;
    private String category;
    private String description;
    private Long rowCount;
    private BigDecimal sizeMb;
    private String owner;
    private String tag;
    private String status;
    private LocalDateTime lastSyncAt;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}