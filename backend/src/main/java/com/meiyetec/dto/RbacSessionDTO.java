package com.meiyetec.dto;

import lombok.Data;

import java.util.List;
import java.util.Set;

/**
 * /auth/me/permissions 返回值。
 * 包含菜单树、角色列表、权限码集合、数据范围元信息。
 */
@Data
public class RbacSessionDTO {
    private Long userId;
    private String username;
    private Long tenantId;
    private String dataScope;
    private List<Long> storeIds;
    private List<String> roles;
    private Set<String> permissions;
    private List<MenuNodeDTO> menus;
}
