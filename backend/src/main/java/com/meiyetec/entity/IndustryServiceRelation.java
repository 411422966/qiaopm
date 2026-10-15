package com.meiyetec.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("industry_service_relation")
public class IndustryServiceRelation {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String industryCode;
    /** SERVICE / PRODUCT / FORM / ROLE */
    private String targetType;
    private String targetCode;
    private String targetName;
    /** 该行业必备 */
    private Integer required;
    private Integer sortOrder;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}