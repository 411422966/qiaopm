package com.meiyetec.security;

import java.util.HashSet;
import java.util.Set;

/**
 * 单次请求的租户/用户/数据范围上下文。
 * 由 TenantInterceptor 在解析 JWT 后写入，由 TenantContextHolder.get() 读取。
 * <p>
 * 字段说明：
 * <ul>
 *   <li>userId / username — 当前登录用户</li>
 *   <li>tenantId — 当前租户，平台管理员可为空（视为跨租户）</li>
 *   <li>storeIds — 数据范围为 STORE/BRAND 时的门店 ID 集合；ALL 时为 null</li>
 *   <li>dataScope — 取最严格集合（ALL < BRAND < STORE < SELF）</li>
 *   <li>permissions — 用户当前会话拥有的权限码集合，用于接口校验</li>
 * </ul>
 */
public class TenantContext {

    private Long userId;
    private String username;
    private Long tenantId;
    private String roleCode;
    private Long brandId;
    private String dataScope;
    private Set<Long> storeIds;
    private Set<String> permissions = new HashSet<>();

    public static TenantContext empty() {
        return new TenantContext();
    }

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public Long getTenantId() { return tenantId; }
    public void setTenantId(Long tenantId) { this.tenantId = tenantId; }

    public String getRoleCode() { return roleCode; }
    public void setRoleCode(String roleCode) { this.roleCode = roleCode; }

    public Long getBrandId() { return brandId; }
    public void setBrandId(Long brandId) { this.brandId = brandId; }

    public String getDataScope() { return dataScope; }
    public void setDataScope(String dataScope) { this.dataScope = dataScope; }

    public Set<Long> getStoreIds() { return storeIds; }
    public void setStoreIds(Set<Long> storeIds) { this.storeIds = storeIds; }

    /** 取 storeIds 中的第一个（适用于单门店角色） */
    public Long getStoreId() {
        if (storeIds == null || storeIds.isEmpty()) return null;
        return storeIds.iterator().next();
    }

    public Set<String> getPermissions() { return permissions; }
    public void setPermissions(Set<String> permissions) { this.permissions = permissions; }

    /**
     * 判断是否拥有某个权限码。
     */
    public boolean hasPermission(String code) {
        return code != null && permissions != null && permissions.contains(code);
    }

    /**
     * 判断是否拥有全部权限码。
     */
    public boolean hasAllPermissions(String... codes) {
        if (codes == null) return true;
        for (String c : codes) if (!hasPermission(c)) return false;
        return true;
    }

    /**
     * 判断数据范围是否允许跨门店。
     * ALL 肯定允许；SELF 必然不允许；其余看 storeIds 是否为 null 或包含 -1（通配）。
     */
    public boolean canCrossStore() {
        if (dataScope == null) return false;
        if ("ALL".equals(dataScope)) return true;
        if (storeIds == null) return false;
        return storeIds.contains(-1L);
    }

    /**
     * 判断当前用户是否能访问指定门店。
     * ALL 始终允许；其它范围只在 storeIds 包含该 storeId 时允许。
     */
    public boolean canAccessStore(Long storeId) {
        if (storeId == null) return true;
        if ("ALL".equals(dataScope)) return true;
        if (storeIds == null) return false;
        return storeIds.contains(storeId) || storeIds.contains(-1L);
    }
}
