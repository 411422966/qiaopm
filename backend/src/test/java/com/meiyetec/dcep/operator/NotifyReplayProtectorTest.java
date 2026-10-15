package com.meiyetec.dcep.operator;

import com.meiyetec.dcep.operator.DcepOperatorProperties.Operator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.lang.reflect.Field;
import java.time.Duration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * DCEP-03/04 阶段 B：NotifyReplayProtector 单元测试
 *
 * <p>覆盖：
 * <ol>
 *   <li>首次接收 → 通过</li>
 *   <li>重复 notifyId → 拒绝</li>
 *   <li>Redis 不可用 → 降级到内存保护</li>
 *   <li>notifyId 为空 → 拒绝（防注入）</li>
 * </ol>
 */
@DisplayName("DCEP-03/04 阶段 B NotifyReplayProtector")
class NotifyReplayProtectorTest {

    private NotifyReplayProtector protector;
    private StringRedisTemplate redis;
    private ValueOperations<String, String> ops;
    private DcepOperatorRegistry registry;

    @BeforeEach
    void setUp() throws Exception {
        protector = new NotifyReplayProtector();
        redis = mock(StringRedisTemplate.class);
        ops = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(ops);

        DcepOperatorProperties props = new DcepOperatorProperties();
        Operator op = new Operator();
        op.setMerchantId("M100");
        op.setNotifyReplayTtlDays(7);
        Map<String, Operator> map = new HashMap<>();
        map.put("ICBC", op);
        Field f = DcepOperatorProperties.class.getDeclaredField("operators");
        f.setAccessible(true);
        f.set(props, map);

        registry = new DcepOperatorRegistry(props);

        Field redisField = NotifyReplayProtector.class.getDeclaredField("redis");
        redisField.setAccessible(true);
        redisField.set(protector, redis);

        Field regField = NotifyReplayProtector.class.getDeclaredField("registry");
        regField.setAccessible(true);
        regField.set(protector, registry);
    }

    private Operator operator() {
        Operator op = new Operator();
        op.setMerchantId("M100");
        op.setNotifyReplayTtlDays(7);
        return op;
    }

    @Test
    @DisplayName("首次接收 → 通过")
    void firstAcquire_pass() {
        when(ops.setIfAbsent(anyString(), anyString(), any(Duration.class))).thenReturn(true);
        boolean ok = protector.tryAcquire(operator(), "notify-001");
        assertTrue(ok);
    }

    @Test
    @DisplayName("重复 notifyId → 拒绝")
    void replay_reject() {
        when(ops.setIfAbsent(anyString(), anyString(), any(Duration.class)))
                .thenReturn(true)   // 第一次
                .thenReturn(false); // 第二次
        Operator op = operator();
        assertTrue(protector.tryAcquire(op, "notify-002"));
        assertFalse(protector.tryAcquire(op, "notify-002"), "重复通知应被拒绝");
    }

    @Test
    @DisplayName("Redis 异常 → 降级到内存保护（不阻塞流程）")
    void redisDown_fallbackToMemory() {
        when(ops.setIfAbsent(anyString(), anyString(), any(Duration.class)))
                .thenThrow(new RuntimeException("Redis connection refused"));
        Operator op = operator();
        assertTrue(protector.tryAcquire(op, "notify-003"), "首次应通过");
        assertFalse(protector.tryAcquire(op, "notify-003"), "重复应被内存 Map 拒绝");
    }

    @Test
    @DisplayName("notifyId 为空 → 拒绝（防注入）")
    void emptyNotifyId_reject() {
        assertFalse(protector.tryAcquire(operator(), null));
        assertFalse(protector.tryAcquire(operator(), ""));
    }

    @Test
    @DisplayName("notifyId 长度 1000 → 仍可通过（不截断）")
    void longNotifyId() {
        when(ops.setIfAbsent(anyString(), anyString(), any(Duration.class))).thenReturn(true);
        String longId = "N".repeat(1000);
        assertTrue(protector.tryAcquire(operator(), longId));
    }

    @Test
    @DisplayName("Redis SETNX 返回 null → 视为拒绝（fail-closed）")
    void setnxNull_reject() {
        when(ops.setIfAbsent(anyString(), anyString(), any(Duration.class))).thenReturn(null);
        assertFalse(protector.tryAcquire(operator(), "notify-004"));
    }

    @Test
    @DisplayName("operator 为 null 时使用全局默认 TTL")
    void nullOperator_useGlobalDefault() {
        when(ops.setIfAbsent(anyString(), anyString(), any(Duration.class))).thenReturn(true);
        assertTrue(protector.tryAcquire(null, "notify-005"));
    }

    @Test
    @DisplayName("clearAll：清理 Redis 与内存 Map")
    void clearAll() throws Exception {
        when(ops.setIfAbsent(anyString(), anyString(), any(Duration.class))).thenReturn(true);
        when(redis.keys(anyString())).thenReturn(new HashSet<>(Set.of("dcep:notify:replay:M100:001",
                "dcep:notify:replay:M100:002")));
        protector.tryAcquire(operator(), "notify-006");

        Field fb = NotifyReplayProtector.class.getDeclaredField("fallbackMap");
        fb.setAccessible(true);
        ConcurrentHashMap<String, Long> map = (ConcurrentHashMap<String, Long>) fb.get(protector);
        map.put("dcep:notify:replay:M100:006", System.currentTimeMillis() + 7 * 86400_000L);

        protector.clearAll("M100");
        // 内存 Map 已清，再次接收同一 notifyId 应通过
        assertTrue(protector.tryAcquire(operator(), "notify-006"), "clearAll 后重新接收应通过");
    }

    @SuppressWarnings("unused")
    private static ConcurrentHashMap<String, Long> cast(Object o) {
        return (ConcurrentHashMap<String, Long>) o;
    }
}
