package com.meiyetec.featureflag;

import com.meiyetec.entity.FeatureFlag;

import java.util.Map;
import java.util.Optional;

/**
 * AI-006：Feature Flag 评估器抽象
 *
 * <p>统一 {@link com.meiyetec.controller.GrayReleaseController} 的评估逻辑，
 * 屏蔽底层 In-Memory / LaunchDarkly 的差异。默认走内存版（无外部依赖）；
 * 设置 {@code feature.launchdarkly.enabled=true} 后自动切换到 LaunchDarkly。
 *
 * <p>方法约定：
 * <ul>
 *   <li>{@link #evaluate(Long, String, Long, Map)}：评估 flag，对每个用户一致</li>
 *   <li>{@link #getFlag(Long, String)}：从缓存获取 flag 配置（用于前端 UI 展示）</li>
 *   <li>{@link #refresh(Long, String)}：强制刷新某条 flag 的本地缓存（写操作后调用）</li>
 *   <li>{@link #mode()}：返回当前实现来源（用于监控/调试）</li>
 * </ul>
 */
public interface FeatureFlagEvaluator {

    /**
     * 评估 feature flag
     *
     * @param tenantId     租户 ID
     * @param flagKey      Flag Key
     * @param userId       评估对象用户 ID（用于白/黑名单 + 百分比）
     * @param extraContext 额外上下文（备用，可为空）
     * @return 评估结果
     */
    EvalResult evaluate(Long tenantId, String flagKey, Long userId, Map<String, Object> extraContext);

    /**
     * 获取 flag 配置（命中本地缓存；缓存未命中时从数据源加载）
     */
    Optional<FeatureFlag> getFlag(Long tenantId, String flagKey);

    /**
     * 强制刷新某条 flag（写操作后调用：创建 / 切换 / 调整比例）
     *
     * <p>对 LaunchDarkly：sdkKey + flagKey 对应的 flag 会通过流式通道自动刷新，本方法做 no-op；
     * 对 In-Memory：清掉本地缓存并立即从 DB 拉取。
     */
    void refresh(Long tenantId, String flagKey);

    /**
     * 返回实现来源标识，用于监控 / 调试
     */
    String mode();

    /**
     * 评估结果
     *
     * @param enabled 是否启用
     * @param reason  原因：match / not_match / disabled / expired / whitelist / blacklist / rollout / no_flag
     */
    record EvalResult(boolean enabled, String reason) {
        public static EvalResult match() {
            return new EvalResult(true, "match");
        }

        public static EvalResult notMatch() {
            return new EvalResult(false, "not_match");
        }

        public static EvalResult noFlag() {
            return new EvalResult(false, "no_flag");
        }
    }
}
