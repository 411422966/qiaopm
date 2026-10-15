package com.meiyetec.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("dim_table")
public class DimTable {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private String dimCode;
    private String dimName;
    private String dimType;
    private String description;
    private String status;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}