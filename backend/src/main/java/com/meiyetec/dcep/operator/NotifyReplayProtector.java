package com.meiyetec.dcep.operator;

import com.meiyetec.dcep.operator.DcepOperatorProperties.Operator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * DCEP-03/04 阶段 B：异步通知防重放保护
 *
 * <p>职责：
 * <ul>
 *   <li>银行回调 notifyId 全局去重（同一 notifyId 不处理两次）</li>
 *   <li>TTL 默认 30 天（覆盖银行对账周期）</li>
 *   <li>Redis 不可用时降级到内存 Map（仅 dev 用）</li>
 * </ul>
 *
 * <p>设计权衡：
 * <ul>
 *   <li>Redis 不可用 → 必须 fail-closed（拒绝重复）而非 fail-open（接受重复），否则会被重放攻击</li>
 *   <li>Redis 可用 → 用 SETNX 保证原子性</li>
 * </ul>
 */
@Slf4j
@Service
@ConditionalOnClass(name = "org.springframework.data.redis.core.StringRedisTemplate")
public class NotifyReplayProtector {

    @Autowired(required = false)
    private StringRedisTemplate redis;

    @Autowired
    private DcepOperatorRegistry registry;

    /** Redis 不可用时的内存兜底（dev 用） */
    private final java.util.concurrent.ConcurrentHashMap<String, Long> fallbackMap =
            new java.util.concurrent.ConcurrentHashMap<>();

    /**
     * 原子登记 + 检查
     *
     * @param operator   运营机构配置（提供 TTL）
     * @param notifyId   银行回调 ID
     * @return true = 首次接收，应处理；false = 重复，应丢弃
     */
    public boolean tryAcquire(Operator operator, String notifyId) {
        if (notifyId == null || notifyId.isEmpty()) {
            log.warn("[DCEP] notifyId 为空，拒绝处理（防注入）");
            return false;
        }
        String key = buildKey(operator, notifyId);
        int ttlDays = operator == null ? registry.getProperties().getDefaultReplayTtlDays()
                : operator.getNotifyReplayTtlDays();

        if (redis != null) {
            try {
                Boolean ok = redis.opsForValue().setIfAbsent(key, "1", Duration.ofDays(ttlDays));
                return Boolean.TRUE.equals(ok);
            } catch (Exception e) {
                log.warn("[DCEP] Redis 不可用，降级到内存保护（仅 dev）：{}", e.getMessage());
                // fail-closed：内存 Map 同样去重
                return tryAcquireInMemory(key, ttlDays);
            }
        }
        return tryAcquireInMemory(key, ttlDays);
    }

    private boolean tryAcquireInMemory(String key, int ttlDays) {
        long nowMs = System.currentTimeMillis();
        long expireMs = nowMs + TimeUnit.DAYS.toMillis(ttlDays);
        Long prev = fallbackMap.putIfAbsent(key, expireMs);
        if (prev == null) return true;
        // 已存在：检查是否过期（惰性清理）
        if (prev < nowMs) {
            fallbackMap.put(key, expireMs);
            return true;
        }
        return false;
    }

    /** 仅用于测试 / 运维：清理某机构所有 notifyId */
    public void clearAll(String operatorKey) {
        String prefix = registry.getProperties().getReplayKeyPrefix() + operatorKey + ":";
        if (redis != null) {
            try {
                java.util.Set<String> keys = redis.keys(prefix + "*");
                if (keys != null && !keys.isEmpty()) redis.delete(keys);
            } catch (Exception e) {
                log.warn("[DCEP] clearAll failed: {}", e.getMessage());
            }
        }
        fallbackMap.keySet().removeIf(k -> k.startsWith(prefix));
    }

    private String buildKey(Operator operator, String notifyId) {
        String operatorKey = "UNKNOWN";
        if (operator != null) {
            // 反推 operator key（通过 merchantId 唯一标识）
            operatorKey = operator.getMerchantId() == null ? "DEFAULT" : operator.getMerchantId();
        }
        return registry.getProperties().getReplayKeyPrefix() + operatorKey + ":" + notifyId;
    }
}
