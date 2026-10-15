package com.meiyetec.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("care_formula")
public class CareFormula {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private String formulaCode;
    private String formulaName;
    private String formulaType;
    private String applicableService;
    private String skinType;
    private String hairType;
    private String ingredientsJson;
    private String stepsJson;
    private Integer durationMin;
    private String caution;
    private String tags;
    private Integer isPremium;
    private String version;
    private Integer useCount;
    private String description;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}