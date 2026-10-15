package com.meiyetec.dto;

import lombok.Data;

@Data
public class RoleUpsertDTO {
    private Long id;
    private String roleCode;
    private String roleName;
    private String description;
    private Integer status;
    private String dataScope;
    private String dataScopeValue;
    /** 权限ID列表（分配权限时传入） */
    private java.util.List<Long> permissionIds;
}
