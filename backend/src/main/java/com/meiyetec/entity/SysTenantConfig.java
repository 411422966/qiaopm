package com.meiyetec.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("sys_tenant_config")
public class SysTenantConfig {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private String cfgKey;
    private String cfgValue;
    private String description;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
