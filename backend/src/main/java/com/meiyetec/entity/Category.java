package com.meiyetec.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;
import java.util.List;

@Data
@TableName("sys_category")
public class Category {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String categoryCode;
    private String categoryName;
    private Long parentId;
    private Integer level;
    private String path;
    private Integer sortOrder;
    private String icon;
    private String categoryType;
    private String description;
    private Integer status;
    
    @TableField(exist = false)
    private List<Category> children;
    
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
    
    @TableLogic
    private Integer deleted;
}
