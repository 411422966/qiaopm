package com.meiyetec.featureflag;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.meiyetec.entity.FeatureFlag;
import com.meiyetec.mapper.FeatureFlagMapper;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * AI-006：In-Memory Feature Flag 评估器（默认实现，无外部依赖）
 *
 * <p>策略：
 * <ul>
 *   <li>本地 ConcurrentHashMap 缓存，key = "{tenantId}:{flagKey}"</li>
 *   <li>{@link #evaluate}：完全本地计算（O(1)），命中后零开销</li>
 *   <li>{@link #refresh}：写操作后由 controller 触发，立即从 DB 拉取刷新</li>
 *   <li>{@link #pollUpdates}：{@code @Scheduled} 每 5s 扫描 DB 上 update_time > 上次轮询时间戳 的 flag，
 *       自动刷新到本地缓存，从而多实例间也能在 5s 内看到其它实例的变更</li>
 * </ul>
 *
 * <p>激活条件：{@code feature.launchdarkly.enabled != true}（默认）
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "feature.launchdarkly", name = "enabled",
        havingValue = "false", matchIfMissing = true)
public class InMemoryFeatureFlagEvaluator implements FeatureFlagEvaluator {

    private final FeatureFlagMapper flagMapper;

    /** 本地缓存：key = "{tenantId}:{flagKey}"，避免跨租户串号 */
    private final Map<String, FeatureFlag> cache = new ConcurrentHashMap<>();

    /** 上次轮询时间戳（每次轮询后推进） */
    private volatile LocalDateTime lastPollTime;

    @PostConstruct
    public void init() {
        // 初始拉一次，启动后即可用
        try {
            this.lastPollTime = LocalDateTime.now().minusYears(1);
            warmUp();
        } catch (Exception e) {
            log.warn("[AI-006] FeatureFlag 初始加载失败（可能是首次启动 / 表不存在）：{}", e.getMessage());
        }
    }

    /**
     * 启动时全量加载
     */
    private void warmUp() {
        List<FeatureFlag> all = flagMapper.selectList(new LambdaQueryWrapper<>());
        for (FeatureFlag f : all) {
            cache.put(keyOf(f.getTenantId(), f.getFlagKey()), f);
        }
        lastPollTime = LocalDateTime.now();
        log.info("[AI-006] FeatureFlag 初始加载：count={}, lastPollTime={}", all.size(), lastPollTime);
    }

    /**
     * AI-006：多实例热加载 — 每 5s 扫描一次 DB，把变更刷新到本地
     */
    @Scheduled(fixedDelayString = "${feature.flag.poll-interval-ms:5000}")
    public void pollUpdates() {
        try {
            LocalDateTime since = lastPollTime == null ? LocalDateTime.now().minusYears(1) : lastPollTime;
            List<FeatureFlag> updates = flagMapper.selectList(
                    new LambdaQueryWrapper<FeatureFlag>()
                            .gt(FeatureFlag::getUpdateTime, since));
            if (updates.isEmpty()) return;
            for (FeatureFlag f : updates) {
                cache.put(keyOf(f.getTenantId(), f.getFlagKey()), f);
                log.info("[AI-006] 热加载 Flag 变更：tenant={} key={} enabled={} rollout={}",
                        f.getTenantId(), f.getFlagKey(), f.getEnabled(), f.getRolloutPercent());
            }
            lastPollTime = LocalDateTime.now();
        } catch (Exception e) {
            log.debug("[AI-006] FeatureFlag 轮询失败（首次启动 / DB 未就绪）：{}", e.getMessage());
        }
    }

    @Override
    public EvalResult evaluate(Long tenantId, String flagKey, Long userId,
                                Map<String, Object> extraContext) {
        FeatureFlag f = cache.get(keyOf(tenantId, flagKey));
        if (f == null) {
            // 缓存未命中 → 从 DB 加载并回填
            FeatureFlag loaded = flagMapper.selectOne(
                    new LambdaQueryWrapper<FeatureFlag>()
                            .eq(FeatureFlag::getTenantId, tenantId)
                            .eq(FeatureFlag::getFlagKey, flagKey));
            if (loaded == null) {
                return EvalResult.noFlag();
            }
            cache.put(keyOf(tenantId, flagKey), loaded);
            f = loaded;
        }
        return internalEval(f, userId);
    }

    private EvalResult internalEval(FeatureFlag f, Long userId) {
        if (f.getEnabled() == null || f.getEnabled() == 0) {
            return new EvalResult(false, "disabled");
        }
        LocalDateTime now = LocalDateTime.now();
        if (f.getStartAt() != null && now.isBefore(f.getStartAt())) {
            return new EvalResult(false, "not_started");
        }
        if (f.getEndAt() != null && now.isAfter(f.getEndAt())) {
            return new EvalResult(false, "expired");
        }
        // 白名单
        if (f.getWhitelist() != null && !f.getWhitelist().isEmpty()) {
            if (f.getWhitelist().contains(String.valueOf(userId))) {
                return new EvalResult(true, "whitelist");
            }
        }
        // 黑名单
        if (f.getBlacklist() != null && !f.getBlacklist().isEmpty()) {
            if (f.getBlacklist().contains(String.valueOf(userId))) {
                return new EvalResult(false, "blacklist");
            }
        }
        // 百分比：HASH(flagKey + userId) % 100 < rolloutPercent
        int percent = f.getRolloutPercent() == null ? 0 : f.getRolloutPercent();
        if (percent <= 0) return new EvalResult(false, "rollout_0");
        if (percent >= 100) return new EvalResult(true, "rollout_100");
        long hash = stableHash(f.getFlagKey() + ":" + userId);
        boolean in = (Math.abs(hash) % 100) < percent;
        return in ? new EvalResult(true, "rollout") : new EvalResult(false, "rollout");
    }

    @Override
    public Optional<FeatureFlag> getFlag(Long tenantId, String flagKey) {
        FeatureFlag f = cache.get(keyOf(tenantId, flagKey));
        if (f != null) return Optional.of(f);
        FeatureFlag loaded = flagMapper.selectOne(
                new LambdaQueryWrapper<FeatureFlag>()
                        .eq(FeatureFlag::getTenantId, tenantId)
                        .eq(FeatureFlag::getFlagKey, flagKey));
        if (loaded != null) {
            cache.put(keyOf(tenantId, flagKey), loaded);
            return Optional.of(loaded);
        }
        return Optional.empty();
    }

    @Override
    public void refresh(Long tenantId, String flagKey) {
        if (flagKey == null) {
            // 全量刷新（极少用：用在 createFlag 后）
            List<FeatureFlag> all = flagMapper.selectList(new LambdaQueryWrapper<>());
            cache.clear();
            for (FeatureFlag f : all) cache.put(keyOf(f.getTenantId(), f.getFlagKey()), f);
            lastPollTime = LocalDateTime.now();
            return;
        }
        FeatureFlag loaded = flagMapper.selectOne(
                new LambdaQueryWrapper<FeatureFlag>()
                        .eq(FeatureFlag::getTenantId, tenantId)
                        .eq(FeatureFlag::getFlagKey, flagKey));
        if (loaded != null) {
            cache.put(keyOf(tenantId, flagKey), loaded);
        } else {
            cache.remove(keyOf(tenantId, flagKey));
        }
        lastPollTime = LocalDateTime.now();
    }

    @Override
    public String mode() {
        return "IN_MEMORY";
    }

    // ========== 工具 ==========

    private static String keyOf(Long tenantId, String flagKey) {
        return tenantId + ":" + flagKey;
    }

    public static long stableHash(String s) {
        try {
            byte[] b = MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8));
            long h = 0;
            for (int i = 0; i < 8; i++) h = (h << 8) | (b[i] & 0xFF);
            return h;
        } catch (Exception e) {
            return s == null ? 0 : s.hashCode();
        }
    }
}
