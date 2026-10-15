package com.meiyetec.security;

import com.meiyetec.entity.SysUser;
import com.meiyetec.exception.BusinessException;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * 请求级上下文：
 * 1. JwtInterceptor 在解析 JWT + 加载用户后调用 set(...) 写入；
 * 2. 业务代码通过 get() 获取当前会话；
 * 3. 异步线程、子线程不会自动继承上下文，禁止跨线程使用。
 *
 * <p>
 * 当前实现基于 ThreadLocal；如后续引入响应式框架，需替换为 ReactiveContext。
 * </p>
 */
@Component
public class TenantContextHolder {

    private static final ThreadLocal<TenantContext> CTX = new ThreadLocal<>();

    public static void set(TenantContext ctx) {
        CTX.set(ctx);
    }

    public static TenantContext get() {
        TenantContext ctx = CTX.get();
        if (ctx == null) {
            // 单元测试 / 定时任务可能直接调用 Service，兜底给一个空上下文
            return TenantContext.empty();
        }
        return ctx;
    }

    public static void clear() {
        CTX.remove();
    }

    /**
     * 要求当前会话已登录，否则抛 401。
     */
    public static TenantContext require() {
        TenantContext ctx = get();
        if (ctx.getUserId() == null) {
            throw BusinessException.unauthorized("未登录");
        }
        return ctx;
    }

    /**
     * 要求当前会话属于指定租户，跨租户直接抛 403。
     */
    public static TenantContext requireTenant(Long tenantId) {
        TenantContext ctx = require();
        if (tenantId != null && ctx.getTenantId() != null && !ctx.getTenantId().equals(tenantId)) {
            // 平台管理员数据范围为 ALL 时，租户维度不强制
            if (!"ALL".equals(ctx.getDataScope())) {
                throw BusinessException.forbidden("跨租户访问被拒绝");
            }
        }
        return ctx;
    }

    public static String getRoleCode() {
        return get().getRoleCode();
    }

    public static Long getBrandId() {
        return get().getBrandId();
    }

    public static Long getStoreId() {
        return get().getStoreId();
    }

    /**
     * 取当前会话的 storeId；未登录或上下文为空时返回 null（用于 INSERT 自动填充，不抛异常）。
     */
    public static Long getStoreIdOrNull() {
        TenantContext ctx = CTX.get();
        return ctx == null ? null : ctx.getStoreId();
    }

    /**
     * 取当前会话的 tenantId；未登录或上下文为空时返回 null（用于 INSERT 自动填充）。
     */
    public static Long getTenantIdOrNull() {
        TenantContext ctx = CTX.get();
        return ctx == null ? null : ctx.getTenantId();
    }

    /**
     * 快速从 SysUser 实体构建上下文（用于登录后立即写入 / 单元测试 fixture）。
     * 只填入 userId / username / tenantId；roleCode / dataScope 推荐走 JwtInterceptor →
     * {@link RbacContextLoader#load(Long)} 完整路径，以同步权限码 + 数据范围门店列表。
     */
    public static TenantContext fromUser(SysUser user) {
        TenantContext ctx = TenantContext.empty();
        if (user != null) {
            ctx.setUserId(user.getId());
            ctx.setUsername(user.getUsername());
            ctx.setTenantId(user.getTenantId());
            ctx.setBrandId(user.getStoreId());
            if (user.getUsername() != null && "admin".equalsIgnoreCase(user.getUsername())) {
                // 兜底：admin 用户通常是超级管理员
                ctx.setRoleCode("SUPER_ADMIN");
                ctx.setDataScope("ALL");
            }
        }
        return ctx;
    }

    /**
     * 高阶版本：调用方拿到完整角色码后调用此方法同步到上下文。
     * 适用于登录响应构建 / 后台任务初始化租户上下文。
     */
    public static TenantContext fromUser(SysUser user, String roleCode, String dataScope) {
        TenantContext ctx = fromUser(user);
        if (roleCode != null) ctx.setRoleCode(roleCode);
        if (dataScope != null) ctx.setDataScope(dataScope);
        return ctx;
    }
}
