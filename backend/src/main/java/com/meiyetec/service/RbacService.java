package com.meiyetec.service;

import com.meiyetec.dto.MenuNodeDTO;
import com.meiyetec.dto.RbacSessionDTO;
import com.meiyetec.dto.RoleUpsertDTO;
import com.meiyetec.entity.SysPermission;
import com.meiyetec.entity.SysRole;
import com.meiyetec.entity.SysRoleDataScope;
import com.meiyetec.entity.SysUser;
import com.meiyetec.exception.BizCode;
import com.meiyetec.exception.BusinessException;
import com.meiyetec.mapper.SysMenuPermissionMapper;
import com.meiyetec.mapper.SysPermissionMapper;
import com.meiyetec.mapper.SysRoleDataScopeMapper;
import com.meiyetec.mapper.SysRoleMapper;
import com.meiyetec.security.TenantContext;
import com.meiyetec.security.TenantContextHolder;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 角色 / 权限码 / 菜单 RBAC 服务。
 * <p>涵盖：</p>
 * <ul>
 *   <li>组装当前用户会话（角色 / 权限码 / 菜单）</li>
 *   <li>角色 CRUD + 数据范围维护</li>
 *   <li>权限码绑定 / 解绑</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class RbacService {

    private final SysPermissionMapper permissionMapper;
    private final SysRoleMapper roleMapper;
    private final SysRoleDataScopeMapper dataScopeMapper;
    private final SysMenuPermissionMapper menuMapper;

    /**
     * 加载当前用户的会话信息（含菜单树）。
     */
    public RbacSessionDTO loadSession(Long userId) {
        TenantContext ctx = TenantContextHolder.get();
        RbacSessionDTO dto = new RbacSessionDTO();
        dto.setUserId(ctx.getUserId());
        dto.setUsername(ctx.getUsername());
        dto.setTenantId(ctx.getTenantId());
        dto.setDataScope(ctx.getDataScope());
        dto.setStoreIds(ctx.getStoreIds() == null ? new ArrayList<>() : new ArrayList<>(ctx.getStoreIds()));
        dto.setRoles(loadRoleCodes(userId));
        dto.setPermissions(ctx.getPermissions());

        List<MenuNodeDTO> menus = buildMenuTree(userId);
        dto.setMenus(menus);
        return dto;
    }

    /**
     * 前端菜单树：直接基于 sys_permission 表的 parent_id 推一棵树。
     * leaf 节点用 path 推 path / component。
     */
    public List<MenuNodeDTO> buildMenuTree(Long userId) {
        List<com.meiyetec.entity.SysMenuPermission> menus = menuMapper.listVisibleMenuByUserId(userId);
        if (menus.isEmpty()) return new ArrayList<>();

        // 加载对应 SysPermission 拿到 title / path
        List<Long> permIds = menus.stream()
                .map(com.meiyetec.entity.SysMenuPermission::getPermissionId)
                .collect(Collectors.toList());
        List<SysPermission> permissions = permissionMapper.selectBatchIds(permIds);
        java.util.Map<Long, SysPermission> permMap = permissions.stream()
                .collect(Collectors.toMap(SysPermission::getId, p -> p));

        // 节点：id = permissionId
        java.util.Map<Long, MenuNodeDTO> nodeMap = new java.util.HashMap<>();
        for (com.meiyetec.entity.SysMenuPermission m : menus) {
            SysPermission p = permMap.get(m.getPermissionId());
            if (p == null) continue;
            MenuNodeDTO node = new MenuNodeDTO();
            node.setId(p.getId());
            node.setPermissionCode(p.getPermissionCode());
            node.setTitle(p.getPermissionName());
            node.setPath(m.getRoutePath());
            node.setComponent(m.getComponentPath());
            node.setRedirect(m.getRedirectPath());
            node.setIcon(m.getIcon());
            node.setSortOrder(m.getSortOrder());
            node.setHidden(m.getHidden());
            node.setChildren(new ArrayList<>());
            nodeMap.put(p.getId(), node);
        }

        // 拼树：依赖 sys_permission.parent_id
        List<MenuNodeDTO> roots = new ArrayList<>();
        for (com.meiyetec.entity.SysMenuPermission m : menus) {
            SysPermission p = permMap.get(m.getPermissionId());
            if (p == null) continue;
            MenuNodeDTO node = nodeMap.get(p.getId());
            Long parentId = p.getParentId();
            if (parentId == null || parentId == 0 || !nodeMap.containsKey(parentId)) {
                roots.add(node);
            } else {
                nodeMap.get(parentId).getChildren().add(node);
            }
        }

        // 排序 & 隐藏空 children
        sortTree(roots);
        return roots;
    }

    private static void sortTree(List<MenuNodeDTO> list) {
        if (list == null) return;
        list.sort(Comparator.comparing(MenuNodeDTO::getSortOrder,
                Comparator.nullsLast(Comparator.naturalOrder())));
        for (MenuNodeDTO n : list) {
            sortTree(n.getChildren());
            if (n.getChildren() != null && n.getChildren().isEmpty()) {
                n.setChildren(null);
            }
        }
    }

    public List<String> loadRoleCodes(Long userId) {
        return roleMapper.getRolesByUserId(userId).stream()
                .map(SysRole::getRoleCode)
                .collect(Collectors.toList());
    }

    // ===== 角色 CRUD =====

    public List<SysRole> listRoles() {
        return roleMapper.selectList(null);
    }

    @Transactional(rollbackFor = Exception.class)
    public SysRole createRole(RoleUpsertDTO dto) {
        if (dto.getRoleCode() == null || dto.getRoleCode().isEmpty()) {
            throw new BusinessException(BizCode.BAD_REQUEST, "roleCode 必填");
        }
        SysRole existing = roleMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<SysRole>()
                        .eq("role_code", dto.getRoleCode()));
        if (existing != null) {
            throw new BusinessException(BizCode.CONFLICT, "角色编码已存在");
        }
        SysRole r = new SysRole();
        r.setRoleCode(dto.getRoleCode());
        r.setRoleName(dto.getRoleName());
        r.setDescription(dto.getDescription());
        r.setStatus(dto.getStatus() == null ? 1 : dto.getStatus());
        roleMapper.insert(r);

        if (dto.getDataScope() != null) {
            SysRoleDataScope s = new SysRoleDataScope();
            s.setRoleId(r.getId());
            s.setDataScope(dto.getDataScope());
            s.setDataScopeValue(dto.getDataScopeValue());
            dataScopeMapper.insert(s);
        }
        return r;
    }

    @Transactional(rollbackFor = Exception.class)
    public void updateRole(Long id, RoleUpsertDTO dto) {
        SysRole r = roleMapper.selectById(id);
        if (r == null) throw new BusinessException(BizCode.NOT_FOUND, "角色不存在");
        if (dto.getRoleName() != null) r.setRoleName(dto.getRoleName());
        if (dto.getDescription() != null) r.setDescription(dto.getDescription());
        if (dto.getStatus() != null) r.setStatus(dto.getStatus());
        roleMapper.updateById(r);

        if (dto.getDataScope() != null) {
            SysRoleDataScope s = dataScopeMapper.selectOne(
                    new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<SysRoleDataScope>()
                            .eq("role_id", id).eq("data_scope", dto.getDataScope()));
            if (s == null) {
                s = new SysRoleDataScope();
                s.setRoleId(id);
                s.setDataScope(dto.getDataScope());
            }
            s.setDataScopeValue(dto.getDataScopeValue());
            if (s.getId() == null) {
                dataScopeMapper.insert(s);
            } else {
                dataScopeMapper.updateById(s);
            }
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public void deleteRole(Long id) {
        SysRole r = roleMapper.selectById(id);
        if (r == null) throw new BusinessException(BizCode.NOT_FOUND, "角色不存在");
        if ("SUPER_ADMIN".equals(r.getRoleCode()) || "PLATFORM_ADMIN".equals(r.getRoleCode())) {
            throw new BusinessException(BizCode.FORBIDDEN, "内置角色不可删除");
        }
        roleMapper.deleteById(id);
        dataScopeMapper.delete(new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<SysRoleDataScope>()
                .eq("role_id", id));
    }

    public Set<String> loadPermissionsByUser(Long userId) {
        Set<String> codes = new HashSet<>();
        for (SysPermission p : permissionMapper.getPermissionsByUserId(userId)) {
            if (p.getPermissionCode() != null) codes.add(p.getPermissionCode());
        }
        return codes;
    }
}
