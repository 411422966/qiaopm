package com.meiyetec.service;

import com.meiyetec.dto.RbacSessionDTO;
import com.meiyetec.entity.SysRole;
import com.meiyetec.mapper.SysMenuPermissionMapper;
import com.meiyetec.mapper.SysPermissionMapper;
import com.meiyetec.mapper.SysRoleDataScopeMapper;
import com.meiyetec.mapper.SysRoleMapper;
import com.meiyetec.security.RbacContextLoader;
import com.meiyetec.security.TenantContext;
import com.meiyetec.security.TenantContextHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * AUTH-007 权限加载 — 集成测试（不打 Mock）
 *
 * 验证 RbacService.loadSession(userId) 的端到端：
 *  1) admin (id=1) → 必返回 permissions 含 '*'，roles 含 SUPER_ADMIN / PLATFORM_ADMIN
 *  2) dataScope 字段必填（ALL / BRAND / STORE / SELF 四选一）
 *  3) menus 数组非空，且含 id / title / path
 *  4) 同一权限码来自 sys_permission JOIN sys_role_permission JOIN sys_user_role 真表
 *     —— 验证手段：permissions 必须有"业务前缀"（booking / wallet / member / commission 等）
 *
 * 设计：
 *  - @SpringBootTest 启动真 Spring 上下文，连真 DB（3307 meiyetec）
 *  - 完全不打 Mock：所有依赖由 Spring 容器注入真实的 MyBatis mapper
 *  - 只读断言：不动 DB 数据
 *  - 真实模拟请求链路：通过 RbacContextLoader.load(userId) 写 ctx → 再调 loadSession(userId)
 *
 * 失败定位：
 *  - permissions 为空 → sys_user_role / sys_role_permission 缺数据
 *  - 缺 '*' → RbacContextLoader line 73-81 逻辑分支没走
 *  - roles 为空 → sys_user_role 关联缺失
 */
@SpringBootTest
@DisplayName("AUTH-007 RbacService.loadSession 集成测试")
class RbacServiceLoadSessionTest {

    @Autowired
    private RbacService rbacService;

    @Autowired
    private RbacContextLoader rbacContextLoader;

    @Autowired
    private SysRoleMapper roleMapper;

    @Autowired
    private SysPermissionMapper permissionMapper;

    @Autowired
    private SysRoleDataScopeMapper dataScopeMapper;

    @Autowired
    private SysMenuPermissionMapper menuMapper;

    @BeforeEach
    void loadAdminContext() {
        // 真实模拟"JwtInterceptor 解析 token → 写 ctx" 这一步
        TenantContext ctx = rbacContextLoader.load(1L);
        TenantContextHolder.set(ctx);
    }

    @AfterEach
    void clearTenantContext() {
        TenantContextHolder.clear();
    }

    @Test
    @DisplayName("admin 加载 session：含 '*' + SUPER_ADMIN 角色")
    void loadSession_admin_returnsSuperAdminSession() {
        RbacSessionDTO dto = rbacService.loadSession(1L);

        assertNotNull(dto, "loadSession 不应返回 null");
        assertEquals(1L, dto.getUserId());
        assertEquals("admin", dto.getUsername());

        // 角色必须含 SUPER_ADMIN 或 PLATFORM_ADMIN
        List<String> roles = dto.getRoles();
        assertNotNull(roles);
        assertTrue(roles.size() > 0, "admin 至少应有 1 个角色");
        boolean hasSuper = roles.stream().anyMatch(r ->
                "SUPER_ADMIN".equals(r) || "PLATFORM_ADMIN".equals(r));
        assertTrue(hasSuper, "admin 必须有 SUPER_ADMIN/PLATFORM_ADMIN 角色，实际: " + roles);

        // 权限必须含 '*'（admin 通配逻辑生效）
        Set<String> perms = dto.getPermissions();
        assertNotNull(perms);
        assertTrue(perms.contains("*"), "admin 必须有 '*' 通配码，实际权限码数量=" + perms.size());
    }

    @Test
    @DisplayName("admin permissions 来源真实 DB（非空 + 必含业务前缀）")
    void loadSession_admin_permissionsSourcedFromRealDb() {
        RbacSessionDTO dto = rbacService.loadSession(1L);
        Set<String> perms = dto.getPermissions();

        // 不应只包含 '*'
        assertTrue(perms.size() > 1, "permissions 应有 DB 真数据，size=" + perms.size());

        // 业务前缀断言：DB sys_permission 真实表里至少有以下前缀
        String[] mustPrefixes = {"booking", "member", "wallet", "setting", "stock", "commission"};
        for (String prefix : mustPrefixes) {
            boolean hit = perms.stream().anyMatch(p -> p.startsWith(prefix));
            assertTrue(hit, "permissions 中至少应有一个 " + prefix + "* 码，实际: " + perms);
        }
    }

    @Test
    @DisplayName("admin dataScope / storeIds 字段非空")
    void loadSession_admin_dataScopeAndStoreIds() {
        RbacSessionDTO dto = rbacService.loadSession(1L);

        assertNotNull(dto.getDataScope(), "dataScope 必填");
        assertTrue(List.of("ALL", "BRAND", "STORE", "SELF").contains(dto.getDataScope()),
                "dataScope 必须在枚举内，实际=" + dto.getDataScope());
        assertNotNull(dto.getStoreIds(), "storeIds 应是 List（非 null）");
        // admin 应是 ALL 范围
        assertEquals("ALL", dto.getDataScope(), "admin 应是 ALL 范围");
    }

    @Test
    @DisplayName("admin menus 数组非空 + 至少一项含 id/title/path")
    void loadSession_admin_menusValid() {
        RbacSessionDTO dto = rbacService.loadSession(1L);

        assertNotNull(dto.getMenus(), "menus 不应为 null");
        assertTrue(dto.getMenus().size() > 0, "menus 不应为空");

        var first = dto.getMenus().get(0);
        assertNotNull(first.getId());
        assertNotNull(first.getTitle(), "menu.title 必填");
        assertNotNull(first.getPath(), "menu.path 必填");
    }

    @Test
    @DisplayName("loadSession 与各 mapper 协作：直接 mapper 也返回非空结果")
    void loadSession_mappersReturnRealData() {
        // 确认 mapper 直接走 DB 也能拿到真数据（证明不是缓存）
        List<SysRole> adminRoles = roleMapper.getRolesByUserId(1L);
        assertTrue(adminRoles.size() >= 2, "admin 至少绑了 2 个角色（SUPER_ADMIN + ADMIN），实际=" + adminRoles.size());

        // 权限码直接查询
        var perms = permissionMapper.getPermissionsByUserId(1L);
        assertTrue(perms.size() > 50, "admin 的 sys_role_permission 至少 50+，实际=" + perms.size());

        // 菜单
        var menus = menuMapper.listVisibleMenuByUserId(1L);
        assertTrue(menus.size() > 0, "admin 的可见菜单应非空");
    }
}