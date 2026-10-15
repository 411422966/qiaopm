package com.meiyetec.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("dashboard")
public class Dashboard {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private String dashboardCode;
    private String dashboardName;
    private String dashboardType;
    private String layoutJson;
    private String metricCodes;
    private String description;
    private Integer isDefault;
    private String status;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}