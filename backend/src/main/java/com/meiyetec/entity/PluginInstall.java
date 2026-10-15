package com.meiyetec.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("plugin_install")
public class PluginInstall {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private Long pluginId;
    private String pluginCode;
    private String version;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime installedAt;
    private LocalDateTime expireAt;
    private String configJson;
    private Integer enabled;
    private String installStatus;
    private String errorMessage;
    private LocalDateTime lastUsedAt;
    private Long useCount;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}