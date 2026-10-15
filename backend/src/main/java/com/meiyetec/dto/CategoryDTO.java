package com.meiyetec.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class CategoryDTO {
    private Long id;
    private String categoryCode;
    
    @NotBlank(message = "分类名称不能为空")
    private String categoryName;
    
    private Long parentId;
    private Integer level;
    private String path;
    private Integer sortOrder;
    private String icon;
    private String categoryType;
    private String description;
    private Integer status;
}
