package com.meiyetec.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("field_desensitization")
public class FieldDesensitization {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    /** phone / idCard / email / bankCard / name */
    private String fieldName;
    private String regex;
    private Integer keepPrefix;
    private Integer keepSuffix;
    private String maskChar;
    private Integer enabled;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}