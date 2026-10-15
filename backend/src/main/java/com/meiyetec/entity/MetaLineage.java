package com.meiyetec.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("meta_lineage")
public class MetaLineage {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private String sourceTableCode;
    private String targetTableCode;
    private String transformation;
    private String lineageType;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}