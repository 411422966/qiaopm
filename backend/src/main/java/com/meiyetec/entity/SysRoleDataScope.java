package com.meiyetec.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 角色数据范围表（sys_role_data_scope）对应实体。
 * 用于 R2.0-A 数据隔离底座：一个角色可同时拥有多个数据范围，
 * 计算时取最严格集合（ALL < BRAND < STORE < SELF）。
 */
@Data
@TableName("sys_role_data_scope")
public class SysRoleDataScope {
    @TableId(type = IdType.AUTO)
    private Long id;

    private Long roleId;

    /** 数据范围：ALL / BRAND / STORE / SELF */
    private String dataScope;

    /** 范围值（逗号分隔的门店/品牌ID；ALL 不填） */
    private String dataScopeValue;

    private String remark;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
