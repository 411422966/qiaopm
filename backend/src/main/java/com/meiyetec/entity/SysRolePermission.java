package com.meiyetec.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 角色-权限关联表
 * sys_role_permission
 */
@Data
@TableName("sys_role_permission")
public class SysRolePermission {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 角色ID */
    private Long roleId;

    /** 权限ID */
    private Long permissionId;

    /** 所属租户（冗余，便于清理） */
    private Long tenantId;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
