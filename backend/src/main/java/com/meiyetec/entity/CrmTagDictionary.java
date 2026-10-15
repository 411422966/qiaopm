package com.meiyetec.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("crm_tag_dictionary")
public class CrmTagDictionary {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private String tagCode;
    private String tagName;
    private String category;
    private String description;
    private String rule;
    private String color;
    private String icon;
    private Integer sortOrder;
    private Integer enabled;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}