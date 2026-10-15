package com.meiyetec.booking;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * EXT-19（后续优化 4）：门店信息查询服务
 *
 * <p>按 {@code storeId} 从 {@code pos_store} 表查真实门店名 + 联系电话，
 * 用于替换 {@code BookingConfirmationService.buildVars()} 里的占位常量。
 *
 * <p>缓存策略：5 分钟本地缓存（避免大促期间推送风暴触发 N+1）。
 */
@Slf4j
@Service
public class StoreLookupService {

    /** 缓存 TTL */
    private static final long CACHE_TTL_MS = TimeUnit.MINUTES.toMillis(5);

    private final JdbcTemplate jdbc;
    private final ConcurrentHashMap<Long, CacheEntry> cache = new ConcurrentHashMap<>();

    public StoreLookupService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * 查询门店名（无则返回 null）
     */
    public String lookupStoreName(Long storeId) {
        return lookup(storeId) == null ? null : lookup(storeId).storeName;
    }

    /**
     * 查询门店电话（无则返回 null）
     */
    public String lookupStorePhone(Long storeId) {
        return lookup(storeId) == null ? null : lookup(storeId).storePhone;
    }

    /** 全量查询（带缓存） */
    public StoreInfo lookup(Long storeId) {
        if (storeId == null) return null;
        long now = System.currentTimeMillis();
        CacheEntry hit = cache.get(storeId);
        if (hit != null && now - hit.cachedAt < CACHE_TTL_MS) return hit.info;

        StoreInfo info;
        try {
            info = jdbc.query(
                    "SELECT store_name, contact_phone FROM pos_store WHERE id=? AND deleted=0",
                    rs -> {
                        if (!rs.next()) return null;
                        StoreInfo i = new StoreInfo();
                        i.storeId = storeId;
                        i.storeName = rs.getString(1);
                        i.storePhone = rs.getString(2);
                        return i;
                    },
                    storeId);
        } catch (Exception e) {
            log.warn("[STORE-LOOKUP] query store={} failed: {}", storeId, e.getMessage());
            info = null;
        }
        if (info != null) {
            cache.put(storeId, new CacheEntry(info, now));
        }
        return info;
    }

    /** 主动失效缓存（store 信息变更时调用） */
    public void invalidate(Long storeId) {
        if (storeId != null) cache.remove(storeId);
    }

    /** 缓存条目 */
    private static class CacheEntry {
        final StoreInfo info;
        final long cachedAt;
        CacheEntry(StoreInfo info, long cachedAt) {
            this.info = info;
            this.cachedAt = cachedAt;
        }
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class StoreInfo {
        private Long storeId;
        private String storeName;
        private String storePhone;
    }
}
