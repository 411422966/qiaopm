package com.meiyetec.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("care_recipe")
public class CareRecipe {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private String recipeCode;
    private String recipeName;
    private String skinType;
    private String concerns;
    private String description;
    private String stepsJson;
    private Integer sessionCount;
    private Integer intervalDays;
    private BigDecimal estimatedCost;
    private String expectedImprovement;
    private String coverImage;
    private String tag;
    private String applicableSeason;
    private String gender;
    private Integer ageMin;
    private Integer ageMax;
    private Integer score;
    private Integer useCount;
    private String status;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}