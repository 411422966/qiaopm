package com.meiyetec.dto;

import lombok.Data;

import java.util.List;

/**
 * 前端路由所需菜单节点。
 * 与 sys_permission + sys_menu_permission 1:1。
 */
@Data
public class MenuNodeDTO {
    private Long id;
    /** 权限码，菜单无权限码时与 path 一致 */
    private String permissionCode;
    private String title;
    private String path;
    private String component;
    private String redirect;
    private String icon;
    private Integer sortOrder;
    private Integer hidden;
    private List<MenuNodeDTO> children;
}
