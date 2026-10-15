package com.meiyetec.security;

import com.meiyetec.entity.SysPermission;
import com.meiyetec.entity.SysRole;
import com.meiyetec.entity.SysRoleDataScope;
import com.meiyetec.entity.SysUser;
import com.meiyetec.exception.BusinessException;
import com.meiyetec.exception.BizCode;
import com.meiyetec.mapper.SysPermissionMapper;
import com.meiyetec.mapper.SysRoleDataScopeMapper;
import com.meiyetec.mapper.SysRoleMapper;
import com.meiyetec.mapper.SysUserMapper;
import com.meiyetec.service.SysTenantService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 根据 userId 一次性组装 TenantContext 数据：
 * 1. 加载用户，校验启用状态；
 * 2. 校验所属租户（启用 + 未过期）；
 * 3. 加载用户角色对应的权限码集合；
 * 4. 加载角色数据范围，按严格优先（ALL < BRAND < STORE < SELF）归并。
 */
@Component
@RequiredArgsConstructor
public class RbacContextLoader {

    private final SysUserMapper userMapper;
    private final SysPermissionMapper permissionMapper;
    private final SysRoleMapper roleMapper;
    private final SysRoleDataScopeMapper dataScopeMapper;
    private final SysTenantService tenantService;

    /**
     * 严格优先级，数值越小越宽松。
     */
    private static final int RANK_ALL = 0;
    private static final int RANK_BRAND = 1;
    private static final int RANK_STORE = 2;
    private static final int RANK_SELF = 3;

    public TenantContext load(Long userId) {
        SysUser user = userMapper.selectById(userId);
        if (user == null) {
            throw BusinessException.unauthorized("用户不存在");
        }
        if (user.getStatus() != null && user.getStatus() == 0) {
            throw new BusinessException(BizCode.AUTH_USER_DISABLED);
        }
        tenantService.checkAvailable(user.getTenantId());

        TenantContext ctx = TenantContext.empty();
        ctx.setUserId(user.getId());
        ctx.setUsername(user.getUsername());
        ctx.setTenantId(user.getTenantId());
        ctx.setBrandId(user.getStoreId()); // 兜底：用户主门店 ID 兼任 brandId（兼容老数据）

        // 角色码（OrgHierarchyService.myScope / hqDashboard 等用 roleCode 判断分支）
        List<SysRole> userRoles = roleMapper.getRolesByUserId(userId);
        if (!userRoles.isEmpty()) {
            ctx.setRoleCode(userRoles.get(0).getRoleCode());
        }

        // 权限码
        List<SysPermission> perms = permissionMapper.getPermissionsByUserId(userId);
        Set<String> codes = new HashSet<>();
        for (SysPermission p : perms) {
            if (p.getPermissionCode() != null) {
                codes.add(p.getPermissionCode());
            }
        }
        // SUPER_ADMIN / PLATFORM_ADMIN 通配 *：避免每个新接口都要补 permission 码
        // 前端 hasPermission('*') 直接 true，后端 PermissionAspect 看到 * 也直接放行
        if (codes.contains("booking") || codes.contains("member") || codes.contains("setting")
                || user.getUsername() != null && "admin".equalsIgnoreCase(user.getUsername())) {
            boolean isSuper = userRoles.stream().anyMatch(r ->
                    "SUPER_ADMIN".equals(r.getRoleCode()) || "PLATFORM_ADMIN".equals(r.getRoleCode()));
            if (isSuper) {
                codes.add("*");
                // 超级管理员若角色码空也兜底（防止配置缺失）
                if (ctx.getRoleCode() == null || ctx.getRoleCode().isEmpty()) {
                    ctx.setRoleCode("SUPER_ADMIN");
                }
            }
        }
        ctx.setPermissions(codes);

        // 数据范围
        List<SysRoleDataScope> scopes = dataScopeMapper.listByUserId(userId);
        if (scopes.isEmpty()) {
            // 没有配置的角色默认 SELF，最严格
            ctx.setDataScope("SELF");
            ctx.setStoreIds(new HashSet<>());
        } else {
            int rank = RANK_SELF;
            String finalScope = "SELF";
            Set<Long> storeIds = new HashSet<>();
            boolean hasAll = false;
            for (SysRoleDataScope s : scopes) {
                String code = s.getDataScope();
                if (code == null) continue;
                int r;
                switch (code) {
                    case "ALL":   r = RANK_ALL;   break;
                    case "BRAND": r = RANK_BRAND; break;
                    case "STORE": r = RANK_STORE; break;
                    case "SELF":  r = RANK_SELF;  break;
                    default:      r = RANK_SELF;
                }
                // 多角色取**最宽松**的数据范围（ALL < BRAND < STORE < SELF）
                if (r < rank) {
                    rank = r;
                    finalScope = code;
                }
                if ("ALL".equals(code)) {
                    hasAll = true;
                }
                if (s.getDataScopeValue() != null && !s.getDataScopeValue().isEmpty()) {
                    Set<Long> ids = Arrays.stream(s.getDataScopeValue().split(","))
                            .map(String::trim)
                            .filter(t -> !t.isEmpty())
                            .map(Long::valueOf)
                            .collect(Collectors.toSet());
                    storeIds.addAll(ids);
                }
            }
            ctx.setDataScope(finalScope);
            if (hasAll) {
                ctx.setStoreIds(new HashSet<>(java.util.Collections.singletonList(-1L)));
            } else {
                // 兜底加入当前用户所属门店
                if (user.getStoreId() != null) storeIds.add(user.getStoreId());
                ctx.setStoreIds(storeIds);
            }
        }

        return ctx;
    }
}
