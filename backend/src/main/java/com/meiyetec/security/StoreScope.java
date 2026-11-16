package com.meiyetec.security;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * 一次请求的「有效门店范围」快照（不可变）。
 *
 * <p>语义分三种，必须显式区分：
 * <ul>
 *   <li>{@link #isAll()} 为 true —— 数据范围为 ALL（平台超管）→ 不加任何门店过滤；</li>
 *   <li>{@link #isEmptyScope()} 为 true —— 数据范围受限但可见门店为空 → 任何列表都必然返回空集；</li>
 *   <li>其余情况 —— 可见门店 = {@code storeIds}（来自 {@code TenantContext.storeIds}，已剔除 -1 通配）。</li>
 * </ul>
 *
 * <p>「空范围」与「不限制」是两种不同语义：前者是「该账号没有任何可见门店」，后者是「可见全部门店」。
 * I186 登记的 N1 / N3 两种退化形态正是把这两者混为一谈导致的。
 *
 * <p>空范围统一用 {@link #EMPTY_SENTINEL}（-9999）兜底，与 DataScopeService 既有实现保持一致。
 */
public final class StoreScope {

    /** 永远不命中的哨兵门店 id（与 DataScopeService 既有实现保持一致）。 */
    public static final long EMPTY_SENTINEL = -9999L;

    private static final StoreScope ALL_SCOPE = new StoreScope(true, Collections.emptyList());
    private static final StoreScope EMPTY_SCOPE = new StoreScope(false, Collections.emptyList());

    private final boolean all;
    private final List<Long> storeIds;

    private StoreScope(boolean all, List<Long> storeIds) {
        this.all = all;
        this.storeIds = Collections.unmodifiableList(storeIds);
    }

    /** 不限制（ALL）。 */
    public static StoreScope all() {
        return ALL_SCOPE;
    }

    /** 限定范围；ids 为 null 或空 → 空范围。 */
    public static StoreScope of(Collection<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return EMPTY_SCOPE;
        }
        List<Long> copy = new ArrayList<>();
        for (Long id : ids) {
            if (id != null) {
                copy.add(id);
            }
        }
        if (copy.isEmpty()) {
            return EMPTY_SCOPE;
        }
        return new StoreScope(false, copy);
    }

    public boolean isAll() {
        return all;
    }

    /** 空范围：范围受限且可见门店为空 → 列表必然空集。 */
    public boolean isEmptyScope() {
        return !all && storeIds.isEmpty();
    }

    public List<Long> getStoreIds() {
        return storeIds;
    }

    /** 可见门店判定（ALL 恒 true；空范围恒 false）。 */
    public boolean contains(Long storeId) {
        if (all) {
            return true;
        }
        if (storeId == null) {
            return false;
        }
        return storeIds.contains(storeId);
    }

    /**
     * 供 MyBatis-Plus wrapper 使用的 id 列表：
     * ALL → 空列表（调用方不拼条件）；空范围 → {@code [-9999]}（拼出 IN (-9999) → 恒空集）。
     */
    public List<Long> resolveIds() {
        if (all) {
            return Collections.emptyList();
        }
        if (storeIds.isEmpty()) {
            return Collections.singletonList(EMPTY_SENTINEL);
        }
        return storeIds;
    }

    /**
     * 生成裸 SQL 条件片段（不含 AND 前缀，由调用方拼接）。
     * 与 {@link #sqlParams()} 必须成对使用，参数顺序一致。
     *
     * <p>ALL → 空串（不拼条件）；空范围 → {@code col IN (-9999)}（恒空集）；其余 → {@code col IN (?,?,...)}。
     */
    public String sqlCondition(String column) {
        if (all) {
            return "";
        }
        if (storeIds.isEmpty()) {
            return column + " IN (" + EMPTY_SENTINEL + ")";
        }
        StringBuilder sb = new StringBuilder(column).append(" IN (");
        for (int i = 0; i < storeIds.size(); i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append('?');
        }
        return sb.append(')').toString();
    }

    /** 与 {@link #sqlCondition(String)} 配套的占位参数。 */
    public List<Object> sqlParams() {
        if (all || storeIds.isEmpty()) {
            return Collections.emptyList();
        }
        return new ArrayList<>(storeIds);
    }
}
