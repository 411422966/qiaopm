package com.meiyetec.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

@Data
@TableName("dim_value")
public class DimValue {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long dimId;
    private String valueCode;
    private String valueName;
    private Long parentId;
    private Integer sortOrder;
    private String extraJson;
    private String status;
}