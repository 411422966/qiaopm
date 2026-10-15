package com.meiyetec.security;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.meiyetec.exception.BizCode;
import com.meiyetec.exception.BusinessException;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * Service 层统一接入数据范围：
 * 1. applyStoreScope(wrapper, storeIdColumn) -> 在已有 QueryWrapper 上追加 store_id 条件；
 * 2. applyTenantScope(wrapper, tenantIdColumn) -> 同上，针对租户；
 * 3. assertStoreScope(storeId) -> 当 storeId 不在 ctx.storeIds 时抛 403。
 *
 * <p>目标：12 个模块的 Mapper 在接 RBAC 时不需要重复写 storeId 过滤逻辑，
 *     只需在自己的 Service 中调用本类即可。</p>
 */
@Component
public class DataScopeService {

    public static final String CTX_KEY_ALL_STORES = "-1";

    /**
     * 当前请求是否可以访问指定 storeId。
     */
    public void assertStoreScope(Long storeId) {
        TenantContext ctx = TenantContextHolder.get();
        if (ctx.getUserId() == null) return;
        if ("ALL".equals(ctx.getDataScope())) return;
        Set<Long> ids = ctx.getStoreIds();
        if (ids == null || ids.isEmpty()) {
            throw new BusinessException(BizCode.STORE_DENIED);
        }
        if (storeId == null) return;
        if (!ids.contains(storeId) && !ids.contains(-1L)) {
            throw new BusinessException(BizCode.STORE_DENIED);
        }
    }

    /**
     * 把数据范围条件写进 QueryWrapper。
     * 当数据范围为 ALL 时不追加；其它情况写死 store_id in (...)，始终保留租户条件。
     */
    public <T> QueryWrapper<T> applyStoreScope(QueryWrapper<T> wrapper, String storeIdColumn) {
        TenantContext ctx = TenantContextHolder.get();
        if (ctx.getUserId() == null) return wrapper;
        if ("ALL".equals(ctx.getDataScope())) return wrapper;
        Set<Long> ids = ctx.getStoreIds();
        if (ids == null || ids.isEmpty()) {
            // 故意写一个永远不命中的条件
            wrapper.eq(storeIdColumn, -9999L);
            return wrapper;
        }
        if (ids.contains(-1L)) return wrapper;
        // MyBatis-Plus in(...)
        wrapper.in(storeIdColumn, ids);
        return wrapper;
    }

    /**
     * 在 LambdaQueryWrapper 上追加数据范围，保留原 Lambda 风格。
     */
    public <T> com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<T> applyStoreScope(
            com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<T> wrapper,
            com.baomidou.mybatisplus.core.toolkit.support.SFunction<T, ?> storeIdGetter) {
        TenantContext ctx = TenantContextHolder.get();
        if (ctx.getUserId() == null) return wrapper;
        if ("ALL".equals(ctx.getDataScope())) return wrapper;
        Set<Long> ids = ctx.getStoreIds();
        if (ids == null || ids.isEmpty()) {
            wrapper.eq(storeIdGetter, -9999L);
            return wrapper;
        }
        if (ids.contains(-1L)) return wrapper;
        wrapper.in(storeIdGetter, ids);
        return wrapper;
    }

    /**
     * 跨租户防护：当前会话 tenantId 与参数 tenantId 不一致且数据范围不是 ALL → 抛错。
     */
    public void assertTenantScope(Long paramTenantId) {
        TenantContext ctx = TenantContextHolder.get();
        if (ctx.getUserId() == null) return;
        if (paramTenantId == null) return;
        if (ctx.getTenantId() != null && ctx.getTenantId().equals(paramTenantId)) return;
        if ("ALL".equals(ctx.getDataScope())) return;
        throw new BusinessException(BizCode.TENANT_DENIED);
    }

    /**
     * 当前用户是否允许新增指定 storeId 的数据。
     */
    public boolean canCreateInStore(Long storeId) {
        TenantContext ctx = TenantContextHolder.get();
        if (ctx.getUserId() == null) return true;
        if ("ALL".equals(ctx.getDataScope())) return true;
        Set<Long> ids = ctx.getStoreIds();
        if (ids == null || ids.isEmpty()) return false;
        if (ids.contains(-1L)) return true;
        return storeId == null || ids.contains(storeId);
    }
}
