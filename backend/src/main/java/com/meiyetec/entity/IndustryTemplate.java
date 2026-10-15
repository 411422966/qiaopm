package com.meiyetec.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("industry_template")
public class IndustryTemplate {
    @TableId(type = IdType.AUTO)
    private Long id;
    /** BEAUTY / MEDSPA / DENTAL / HAIR / MATERNITY */
    private String industryCode;
    private String industryName;
    private String description;
    private String defaultServices;
    private String defaultProducts;
    private String defaultForms;
    private Integer enabled;
    private Integer sortOrder;
    private String icon;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}