package com.meiyetec.featureflag;

import com.launchdarkly.sdk.ContextBuilder;
import com.launchdarkly.sdk.LDContext;
import com.launchdarkly.sdk.LDValue;
import com.launchdarkly.sdk.server.Components;
import com.launchdarkly.sdk.server.LDClient;
import com.launchdarkly.sdk.server.LDConfig;
import com.meiyetec.entity.FeatureFlag;
import com.meiyetec.mapper.FeatureFlagMapper;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

/**
 * AI-006：LaunchDarkly Feature Flag 评估器 — SDK 7.x 生产级实现
 *
 * <p>替代内存版的 in-process cache，把 flag source-of-truth 委托给 LaunchDarkly 服务。
 *
 * <p>配置项（application.yml）：
 * <pre>
 * feature:
 *   launchdarkly:
 *     enabled: true
 *     sdk-key: sdk-xxxxxxxxxxxxxx     # 在 https://app.launchdarkly.com 创建
 *     offline: false                   # true=本地评估，不连 LD（开发/无网环境）
 *     streaming: true                  # SSE 流订阅，毫秒级热更新
 *     init-timeout-ms: 5000            # 启动阻塞等待首次 flag 拉取
 *     poll-interval-ms: 300000        # 非 streaming 模式轮询间隔（默认 5 分钟）
 * </pre>
 *
 * <p>激活条件：{@code feature.launchdarkly.enabled=true}
 *
 * <p>实验规则使用方式（业务代码）：
 * <pre>
 * FeatureFlagEvaluator.EvalResult r = evaluator.evaluate(tenantId, "bnpl-klarna-enabled", userId, null);
 * if (r.enabled()) { ... }
 * </pre>
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "feature.launchdarkly", name = "enabled", havingValue = "true")
public class LaunchDarklyFeatureFlagEvaluator implements FeatureFlagEvaluator {

    @Autowired(required = false)
    private LaunchDarklyProperties properties;

    @Autowired(required = false)
    private FeatureFlagMapper flagMapper;

    /** 单例 LD 客户端 */
    private volatile LDClient ldClient;
    /** offline 模式下的快照引用 */
    private volatile LDContext offlineContext;
    private volatile boolean initFailed = false;

    // ====================================================================
    // 生命周期
    // ====================================================================

    @PostConstruct
    public void init() {
        if (properties == null || !properties.isEnabled()) {
            log.warn("[AI-006] LaunchDarkly enabled=false，不初始化客户端");
            initFailed = true;
            return;
        }
        if (properties.isOffline()) {
            log.info("[AI-006] LaunchDarkly offline 模式（不连接 LD 服务）");
            initFailed = false;
            return;
        }
        ensureClient();
    }

    private synchronized LDClient ensureClient() {
        if (ldClient != null) return ldClient;
        if (initFailed) return null;

        String sdkKey = properties.getSdkKey();
        if (sdkKey == null || sdkKey.isBlank() || sdkKey.startsWith("${")) {
            log.warn("[AI-006] LaunchDarkly sdk-key 未配置，跳过初始化");
            initFailed = true;
            return null;
        }

        try {
            LDConfig.Builder configBuilder = new LDConfig.Builder();

            if (properties.isStreaming()) {
                // SDK 7.x：使用 Components.streamingDataSource() 配置 SSE 流订阅
                // 变更通过 SSE 推送，毫秒级热更新（推荐）
                configBuilder.dataSource(Components.streamingDataSource());
            } else {
                // SDK 7.x：使用 Components.pollingDataSource() 配置轮询
                // 每 pollInterval 拉取一次（适用于网络受限环境）
                Duration pollInterval = properties.getPollIntervalMs() != null
                        ? Duration.ofMillis(properties.getPollIntervalMs())
                        : Duration.ofSeconds(30);
                configBuilder.dataSource(Components.pollingDataSource().pollInterval(pollInterval));
            }

            // 启动阻塞等待首次 flag 拉取完成（避免启动后第一批请求降级）
            if (properties.getInitTimeoutMs() != null && properties.getInitTimeoutMs() > 0) {
                configBuilder.startWait(Duration.ofMillis(properties.getInitTimeoutMs()));
            }

            LDConfig config = configBuilder.build();
            ldClient = new LDClient(sdkKey, config);

            if (!ldClient.isInitialized()) {
                log.warn("[AI-006] LaunchDarkly 首次连接中（startWaitTime={}ms），后续请求将等待或降级",
                        properties.getInitTimeoutMs());
            } else {
                int mask = Math.min(4, sdkKey.length());
                log.info("[AI-006] LaunchDarkly 已连接：sdkKey={}**** streaming={}",
                        sdkKey.substring(0, mask), properties.isStreaming());
            }
            return ldClient;

        } catch (Exception e) {
            log.error("[AI-006] LaunchDarkly 初始化失败：{}", e.getMessage());
            initFailed = true;
            return null;
        }
    }

    @PreDestroy
    public void shutdown() {
        if (ldClient != null) {
            try {
                ldClient.close();
                ldClient = null;
                log.info("[AI-006] LaunchDarkly 客户端已关闭");
            } catch (Exception e) {
                log.warn("[AI-006] LD 客户端关闭异常：{}", e.getMessage());
            }
        }
    }

    // ====================================================================
    // FeatureFlagEvaluator 实现
    // ====================================================================

    @Override
    public EvalResult evaluate(Long tenantId, String flagKey, Long userId,
                              Map<String, Object> extraContext) {
        // Offline 模式：用 DB fallback
        if (isOffline()) {
            return fallback(tenantId, flagKey, userId, extraContext);
        }

        LDClient client = ensureClient();
        if (client == null) {
            return fallback(tenantId, flagKey, userId, extraContext);
        }

        try {
            LDContext ctx = buildContext(tenantId, userId, extraContext);

            // SDK 7.x：boolVariation(key, context, defaultValue)
            boolean enabled = client.boolVariation(flagKey, ctx, false);

            // 获取 evaluation detail（含 reason）
            var detail = client.boolVariationDetail(flagKey, ctx, false);
            String reason = mapEvalReason(detail.getReason());

            log.debug("[AI-006] LD evaluate: key={} userId={} enabled={} reason={}",
                    flagKey, userId, enabled, reason);
            return new EvalResult(enabled, reason);

        } catch (Exception e) {
            log.warn("[AI-006] LD evaluate 异常，降级 DB：key={} err={}", flagKey, e.getMessage());
            return fallback(tenantId, flagKey, userId, extraContext);
        }
    }

    @Override
    public Optional<FeatureFlag> getFlag(Long tenantId, String flagKey) {
        if (flagMapper == null) return Optional.empty();
        return Optional.ofNullable(flagMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<FeatureFlag>()
                        .eq(FeatureFlag::getTenantId, tenantId)
                        .eq(FeatureFlag::getFlagKey, flagKey)));
    }

    @Override
    public void refresh(Long tenantId, String flagKey) {
        // LD streaming 模式：SDK 会自动接收变更推送，force refresh 仅用于调试
        if (flagKey != null) {
            log.debug("[AI-006] refresh({}, {}) — streaming 模式由 SDK 自动推送", tenantId, flagKey);
        }
    }

    @Override
    public String mode() {
        if (isOffline()) return "LAUNCHDARKLY_OFFLINE";
        if (initFailed || ldClient == null) return "LAUNCHDARKLY_INIT_FAILED";
        return "LAUNCHDARKLY";
    }

    // ====================================================================
    // 内部工具
    // ====================================================================

    private boolean isOffline() {
        return properties != null && properties.isOffline();
    }

    /**
     * 构造 LD Context（SDK 7.x multi-kind context）。
     *
     * <p>Kind 约定：
     * <ul>
     *   <li>{@code "user"} — 以 userId 为 identifier（面向 C 端用户）</li>
     *   <li>{@code "device"} — 以 deviceCode 为 identifier（面向 IoT 设备）</li>
     * </ul>
     *
     * <p>在 LaunchDarkly dashboard 配置 targeting rule 时：
     * <pre>
     *   - kind: user
     *     attribute: key
     *     match: userId
     *   - kind: user
     *     attribute: tenantId
     *     operator: in
     *     values: [1, 2, 3]
     * </pre>
     */
    private LDContext buildContext(Long tenantId, Long userId, Map<String, Object> extra) {
        String ctxKey = key(tenantId, userId);

        // 单 kind 上下文（最常用：user 为 kind）
        ContextBuilder b = LDContext.builder(com.launchdarkly.sdk.ContextKind.of("user"), ctxKey);
        if (tenantId != null) b.set("tenantId", tenantId.doubleValue());
        if (userId != null) b.set("userId", userId.doubleValue());
        b.set("anonymous", false);

        if (extra != null) {
            extra.forEach((k, v) -> {
                if (v == null) {
                    b.set(k, LDValue.ofNull());
                } else if (v instanceof Number n) {
                    b.set(k, n.doubleValue());
                } else if (v instanceof Boolean bo) {
                    b.set(k, bo);
                } else {
                    b.set(k, v.toString());
                }
            });
        }

        return b.build();
    }

    private static String key(Long tenantId, Long userId) {
        return (tenantId == null ? 0 : tenantId) + ":" + (userId == null ? 0 : userId);
    }

    /**
     * Map LD evaluation reason 到内部 reason 字符串
     */
    private static String mapEvalReason(com.launchdarkly.sdk.EvaluationReason reason) {
        if (reason == null) return "unknown";
        return switch (reason.getKind()) {
            case OFF -> "disabled";
            case PREREQUISITE_FAILED -> "prerequisite_failed";
            case RULE_MATCH -> "rule_match";
            case FALLTHROUGH -> "fallthrough";
            case TARGET_MATCH -> "target_match";
            case ERROR -> "error:" + reason.getErrorKind();
            default -> "unknown";
        };
    }

    /**
     * Fallback：LD 不可用时用 DB 行做 hash rollout
     */
    private EvalResult fallback(Long tenantId, String flagKey, Long userId, Map<String, Object> extra) {
        if (flagMapper == null) return EvalResult.noFlag();
        FeatureFlag f = flagMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<FeatureFlag>()
                        .eq(FeatureFlag::getTenantId, tenantId)
                        .eq(FeatureFlag::getFlagKey, flagKey));
        if (f == null) return EvalResult.noFlag();

        boolean enabled = f.getEnabled() != null && f.getEnabled() == 1;
        if (!enabled) return new EvalResult(false, "db_disabled");

        // 时间窗口
        java.time.LocalDateTime now = java.time.LocalDateTime.now();
        if (f.getStartAt() != null && now.isBefore(f.getStartAt())) {
            return new EvalResult(false, "db_not_started");
        }
        if (f.getEndAt() != null && now.isAfter(f.getEndAt())) {
            return new EvalResult(false, "db_expired");
        }

        // 白/黑名单
        if (f.getWhitelist() != null && f.getWhitelist().contains(String.valueOf(userId))) {
            return new EvalResult(true, "db_whitelist");
        }
        if (f.getBlacklist() != null && f.getBlacklist().contains(String.valueOf(userId))) {
            return new EvalResult(false, "db_blacklist");
        }

        // 百分比灰度
        Integer pct = f.getRolloutPercent();
        if (pct == null || pct <= 0) {
            return new EvalResult(false, "db_rollout_0");
        }
        if (pct >= 100) {
            return new EvalResult(true, "db_rollout_100");
        }
        long h = InMemoryFeatureFlagEvaluator.stableHash(f.getFlagKey() + ":" + userId);
        boolean in = (Math.abs(h) % 100) < pct;
        return new EvalResult(in, in ? "db_rollout_match" : "db_rollout_no_match");
    }

    // ====================================================================
    // 配置属性
    // ====================================================================

    /**
     * LaunchDarkly 配置属性（对应 application.yml 的 feature.launchdarkly.*）
     */
    @Data
    @Configuration
    @ConfigurationProperties(prefix = "feature.launchdarkly")
    public static class LaunchDarklyProperties {
        /** 总开关 */
        private boolean enabled = false;

        /** SDK Key（在 https://app.launchdarkly.com → Account Settings → SDK Key） */
        private String sdkKey;

        /**
         * Offline 模式（不连 LD 服务，用于开发/无网环境）。
         * 开启后所有 evaluate 返回 {@code defaultValue=false}，
         * 可通过 feature_flag 表的 DB 行做本地灰度。
         */
        private boolean offline = false;

        /**
         * 是否使用 SSE 流订阅（推荐：true）。
         * false = 轮询模式（每 poll-interval-ms 拉取一次）
         */
        private boolean streaming = true;

        /**
         * 启动阻塞等待首次 flag 拉取完成的时间（ms）。
         * 设为 0 则不等待（异步连接，后续请求可能短暂降级到 DB fallback）。
         */
        private Integer initTimeoutMs = 5000;

        /**
         * 非 streaming 模式的轮询间隔（ms）。
         * streaming=true 时忽略。
         */
        private Integer pollIntervalMs = 30_000;
    }
}
