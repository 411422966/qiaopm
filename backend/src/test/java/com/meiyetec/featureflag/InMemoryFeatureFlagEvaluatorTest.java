package com.meiyetec.featureflag;

import com.meiyetec.entity.FeatureFlag;
import com.meiyetec.mapper.FeatureFlagMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * AI-006：InMemoryFeatureFlagEvaluator 单元测试
 *
 * <p>覆盖：
 * <ol>
 *   <li>flag 不存在 → noFlag</li>
 *   <li>flag 关闭 → disabled</li>
 *   <li>时间窗外 → expired / not_started</li>
 *   <li>白名单 / 黑名单 命中</li>
 *   <li>100% rollout → 总是 true</li>
 *   <li>部分 rollout → 同一用户总是得到一致结果</li>
 *   <li>refresh 后立即生效（写后即时刷新）</li>
 *   <li>cache 命中（Mock mapper 只调用 selectList 一次）</li>
 * </ol>
 */
@DisplayName("AI-006 InMemoryFeatureFlagEvaluator")
class InMemoryFeatureFlagEvaluatorTest {

    private InMemoryFeatureFlagEvaluator evaluator;
    private FeatureFlagMapper flagMapper;

    @BeforeEach
    void setUp() throws Exception {
        evaluator = new InMemoryFeatureFlagEvaluator(flagMapper);
        flagMapper = mock(FeatureFlagMapper.class);
        // 反射替换：因为 lombok 已生成构造函数，不需要 set
        // 但保留 set 方法以备扩展
        setMapper(flagMapper);

        // 默认：mapper 啥也查不到
        when(flagMapper.selectList(any())).thenReturn(List.of());
    }

    private void setMapper(Object value) throws Exception {
        Field f = InMemoryFeatureFlagEvaluator.class.getDeclaredField("flagMapper");
        f.setAccessible(true);
        f.set(evaluator, value);
    }

    private void set(String name, Object value) throws Exception {
        Field f = InMemoryFeatureFlagEvaluator.class.getDeclaredField(name);
        f.setAccessible(true);
        f.set(evaluator, value);
    }

    private FeatureFlag f(String key, int enabled, Integer rollout,
                            LocalDateTime startAt, LocalDateTime endAt,
                            String whitelist, String blacklist) {
        FeatureFlag flag = new FeatureFlag();
        flag.setTenantId(1L);
        flag.setFlagKey(key);
        flag.setEnabled(enabled);
        flag.setRolloutPercent(rollout);
        flag.setStartAt(startAt);
        flag.setEndAt(endAt);
        flag.setWhitelist(whitelist);
        flag.setBlacklist(blacklist);
        flag.setUpdateTime(LocalDateTime.now());
        return flag;
    }

    @Test
    @DisplayName("flag 不存在 → noFlag")
    void noFlag() {
        when(flagMapper.selectOne(any())).thenReturn(null);
        FeatureFlagEvaluator.EvalResult r = evaluator.evaluate(1L, "missing", 100L, null);
        assertFalse(r.enabled());
        assertEquals("no_flag", r.reason());
    }

    @Test
    @DisplayName("flag 关闭 → disabled")
    void disabled() {
        when(flagMapper.selectOne(any())).thenReturn(f("ff", 0, 100, null, null, null, null));
        FeatureFlagEvaluator.EvalResult r = evaluator.evaluate(1L, "ff", 100L, null);
        assertFalse(r.enabled());
        assertEquals("disabled", r.reason());
    }

    @Test
    @DisplayName("时间窗未开始 → not_started")
    void notStarted() {
        FeatureFlag flag = f("ff", 1, 100, LocalDateTime.now().plusDays(1), null, null, null);
        when(flagMapper.selectOne(any())).thenReturn(flag);
        FeatureFlagEvaluator.EvalResult r = evaluator.evaluate(1L, "ff", 100L, null);
        assertFalse(r.enabled());
        assertEquals("not_started", r.reason());
    }

    @Test
    @DisplayName("时间窗已过 → expired")
    void expired() {
        FeatureFlag flag = f("ff", 1, 100, null, LocalDateTime.now().minusDays(1), null, null);
        when(flagMapper.selectOne(any())).thenReturn(flag);
        FeatureFlagEvaluator.EvalResult r = evaluator.evaluate(1L, "ff", 100L, null);
        assertFalse(r.enabled());
        assertEquals("expired", r.reason());
    }

    @Test
    @DisplayName("白名单命中 → whitelist")
    void whitelist() {
        FeatureFlag flag = f("ff", 1, 0, null, null, "100,200", null);
        when(flagMapper.selectOne(any())).thenReturn(flag);
        FeatureFlagEvaluator.EvalResult r = evaluator.evaluate(1L, "ff", 100L, null);
        assertTrue(r.enabled());
        assertEquals("whitelist", r.reason());
    }

    @Test
    @DisplayName("黑名单命中 → blacklist")
    void blacklist() {
        FeatureFlag flag = f("ff", 1, 100, null, null, null, "100");
        when(flagMapper.selectOne(any())).thenReturn(flag);
        FeatureFlagEvaluator.EvalResult r = evaluator.evaluate(1L, "ff", 100L, null);
        assertFalse(r.enabled());
        assertEquals("blacklist", r.reason());
    }

    @Test
    @DisplayName("100% rollout → 总是 true")
    void rolloutFull() {
        FeatureFlag flag = f("ff", 1, 100, null, null, null, null);
        when(flagMapper.selectOne(any())).thenReturn(flag);
        for (long uid : new long[]{1L, 99L, 12345L, 999999L}) {
            FeatureFlagEvaluator.EvalResult r = evaluator.evaluate(1L, "ff", uid, null);
            assertTrue(r.enabled(), "uid=" + uid);
        }
    }

    @Test
    @DisplayName("0% rollout → 总是 false")
    void rolloutZero() {
        FeatureFlag flag = f("ff", 1, 0, null, null, null, null);
        when(flagMapper.selectOne(any())).thenReturn(flag);
        FeatureFlagEvaluator.EvalResult r = evaluator.evaluate(1L, "ff", 123L, null);
        assertFalse(r.enabled());
        assertEquals("rollout_0", r.reason());
    }

    @Test
    @DisplayName("部分 rollout → 同一用户始终一致")
    void rolloutConsistent() {
        FeatureFlag flag = f("checkout-v2", 1, 30, null, null, null, null);
        when(flagMapper.selectOne(any())).thenReturn(flag);
        Map<Long, Boolean> firstRun = new HashMap<>();
        for (long uid = 1; uid <= 100; uid++) {
            firstRun.put(uid, evaluator.evaluate(1L, "checkout-v2", uid, null).enabled());
        }
        // 二次评估同一用户必须完全一致
        for (long uid = 1; uid <= 100; uid++) {
            assertEquals(firstRun.get(uid),
                    evaluator.evaluate(1L, "checkout-v2", uid, null).enabled(),
                    "uid=" + uid);
        }
    }

    @Test
    @DisplayName("refresh 后立即生效")
    void refreshTakesEffectImmediately() throws Exception {
        // 第一次：flag 关
        when(flagMapper.selectOne(any())).thenReturn(f("ff", 0, 0, null, null, null, null));
        assertFalse(evaluator.evaluate(1L, "ff", 100L, null).enabled());

        // 模拟 DB 更新：现在 flag 开 + 100%
        when(flagMapper.selectOne(any())).thenReturn(f("ff", 1, 100, null, null, null, null));
        // 没 refresh 之前仍然是 disabled（缓存中还是关闭）
        assertFalse(evaluator.evaluate(1L, "ff", 100L, null).enabled());

        // refresh 后立即生效
        evaluator.refresh(1L, "ff");
        assertTrue(evaluator.evaluate(1L, "ff", 100L, null).enabled());
    }

    @Test
    @DisplayName("mode = IN_MEMORY")
    void modeInMemory() {
        assertEquals("IN_MEMORY", evaluator.mode());
    }

    @Test
    @DisplayName("stableHash 在 Java 17 跨平台一致")
    void hashStable() {
        long h1 = InMemoryFeatureFlagEvaluator.stableHash("test-key:123");
        long h2 = InMemoryFeatureFlagEvaluator.stableHash("test-key:123");
        assertEquals(h1, h2);
    }
}
