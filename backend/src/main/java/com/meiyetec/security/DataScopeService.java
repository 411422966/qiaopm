package com.meiyetec.security;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.support.SFunction;
import com.meiyetec.exception.BizCode;
import com.meiyetec.exception.BusinessException;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * 数据范围（门店维度）的统一入口，承担两个职责：
 *
 * <ol>
 *   <li><b>工具方法</b>：applyStoreScope / resolveStoreScope / resolveEffectiveStoreScope 等，
 *       供 Service 层把「当前会话有效门店集合」注入到查询条件；</li>
 *   <li><b>校验方法</b>：assertStoreScope / resolveWritableStoreId 等，做 Service 层纵深防御
 *       （DataScopeInterceptor 是第一层，本类是第二层；拦截器依赖请求参数采集，
 *       路径参数与 body 数组形态采集不到，Service 层必须能独立拦下越权）。</li>
 * </ol>
 *
 * <p>历史说明：本类注释曾写「12 个模块的 Mapper 在接 RBAC 时调用本类即可」，
 *    但经全仓检索，该承诺从未落地（0 调用方）；自 I186 第 1 批起本类正式启用。</p>
 *
 * <p>缺省语义（I186 决策）：调用方未显式指定门店时，查询范围 = 当前会话的有效门店集合
 *    （{@code ctx.storeIds}），而不是「不过滤」也不是「恒空」。</p>
 */
@Component
public class DataScopeService {

    public static final String CTX_KEY_ALL_STORES = "-1";

    /**
     * 解析当前会话的有效门店范围。
     *
     * <p>语义分三种：未登录 / dataScope=ALL / storeIds 含 -1 → {@link StoreScope#all()}（不限制）；
     * 其余按 {@code ctx.storeIds} 收敛；集合为空 → 空范围（查询必然空集）。</p>
     */
    public StoreScope resolveStoreScope() {
        TenantContext ctx = TenantContextHolder.get();
        if (ctx.getUserId() == null) return StoreScope.all();
        if ("ALL".equals(ctx.getDataScope())) return StoreScope.all();
        Set<Long> ids = ctx.getStoreIds();
        if (ids == null || ids.isEmpty()) {
            // 范围受限但可见门店为空 → 空范围（不是「不限制」）
            return StoreScope.of(Collections.emptyList());
        }
        if (ids.contains(-1L)) return StoreScope.all();
        List<Long> copy = new ArrayList<>();
        for (Long id : ids) {
            if (id != null) copy.add(id);
        }
        return StoreScope.of(copy);
    }

    /**
     * 缺省门店：仅当有效范围恰好收敛到 1 个门店时返回它；多店返回 null（调用方须自行决策）。
     */
    public Long resolveDefaultStoreId() {
        StoreScope scope = resolveStoreScope();
        if (scope.isAll()) return null;
        List<Long> ids = scope.getStoreIds();
        return ids.size() == 1 ? ids.get(0) : null;
    }

    /**
     * 列表 / 统计 / 导出类端点的门店过滤裁决。
     *
     * <p>显式指定门店且越权 → 403 STORE_DENIED（Service 层纵深防御，拦截器之外的第二道闸）；
     * 显式指定且合法 → 收敛为该门店；缺省 → 当前会话有效范围（ALL 时不限制）。</p>
     */
    public StoreScope resolveEffectiveStoreScope(Long requestedStoreId) {
        StoreScope scope = resolveStoreScope();
        if (requestedStoreId != null) {
            if (!scope.contains(requestedStoreId)) {
                throw new BusinessException(BizCode.STORE_DENIED);
            }
            return StoreScope.of(List.of(requestedStoreId));
        }
        return scope;
    }

    /**
     * 列表类端点用的 id 集合：null 表示「不限制」（ALL），否则为需要 IN 的 id 列表
     * （空范围 → {@code [-9999]} → 恒空集）。
     */
    public List<Long> resolveStoreIds(Long requestedStoreId) {
        StoreScope scope = resolveEffectiveStoreScope(requestedStoreId);
        return scope.isAll() ? null : scope.resolveIds();
    }

    /**
     * 写操作用的门店裁决：把「调用方指定的门店」与「有效范围」求交。
     *
     * <p>显式指定且越权 → 403；缺省且范围唯一 → 该门店；缺省且多店 → 400（请显式指定）；
     * 缺省且 ALL → 400（要求显式指定，避免再次出现硬编码门店 1）。</p>
     */
    public Long resolveWritableStoreId(Long requestedStoreId) {
        StoreScope scope = resolveStoreScope();
        if (requestedStoreId != null) {
            if (!scope.contains(requestedStoreId)) {
                throw new BusinessException(BizCode.STORE_DENIED);
            }
            return requestedStoreId;
        }
        if (scope.isAll()) {
            throw BusinessException.badRequest("storeId 必填");
        }
        if (scope.isEmptyScope()) {
            throw new BusinessException(BizCode.STORE_DENIED);
        }
        if (scope.getStoreIds().size() > 1) {
            throw BusinessException.badRequest("当前账号可访问多个门店，请指定 storeId");
        }
        return scope.getStoreIds().get(0);
    }

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
    public <T> LambdaQueryWrapper<T> applyStoreScope(
            LambdaQueryWrapper<T> wrapper,
            SFunction<T, ?> storeIdGetter) {
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
     * QueryWrapper 版本（带显式门店裁决）：显式门店越权 → 403；缺省 → 按有效集合过滤。
     */
    public <T> QueryWrapper<T> applyStoreScope(QueryWrapper<T> wrapper,
                                                String storeIdColumn,
                                                Long requestedStoreId) {
        StoreScope scope = resolveEffectiveStoreScope(requestedStoreId);
        if (scope.isAll()) return wrapper;
        return wrapper.in(storeIdColumn, scope.resolveIds());
    }

    /**
     * LambdaQueryWrapper 版本（带显式门店裁决）：显式门店越权 → 403；缺省 → 按有效集合过滤。
     */
    public <T> LambdaQueryWrapper<T> applyStoreScope(LambdaQueryWrapper<T> wrapper,
                                                     SFunction<T, ?> storeIdGetter,
                                                     Long requestedStoreId) {
        StoreScope scope = resolveEffectiveStoreScope(requestedStoreId);
        if (scope.isAll()) return wrapper;
        return wrapper.in(storeIdGetter, scope.resolveIds());
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
