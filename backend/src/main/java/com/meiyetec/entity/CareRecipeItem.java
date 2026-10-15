package com.meiyetec.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

@Data
@TableName("care_recipe_item")
public class CareRecipeItem {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long recipeId;
    private Long serviceId;
    private String itemType;
    private String itemName;
    private String description;
    private String usageQty;
    private Integer sequence;
    private Integer estimatedMinutes;
}