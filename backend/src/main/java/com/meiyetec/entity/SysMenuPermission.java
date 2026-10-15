package com.meiyetec.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 菜单 UI 扩展表（sys_menu_permission）对应实体。
 * 复用 sys_permission 提供路由 path、组件、图标等 UI 字段。
 */
@Data
@TableName("sys_menu_permission")
public class SysMenuPermission {
    @TableId(type = IdType.AUTO)
    private Long id;

    private Long permissionId;
    private String routePath;
    private String componentPath;
    private String redirectPath;
    private String icon;
    private String activePaths;
    private Integer hidden;
    private Integer keepAlive;
    private Integer sortOrder;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
