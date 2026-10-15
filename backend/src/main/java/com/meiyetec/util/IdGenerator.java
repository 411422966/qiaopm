package com.meiyetec.util;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicLong;

/**
 * P0-MONEY-003 · 雪花算法 ID 生成器（替换 Math.random() / new Random()）
 *
 * <p>结构（64 bit）：
 * <pre>
 *  0 - 00000000000000000000000000000000000000000 - 0000000000 - 000000000000
 *  ^   sign                                       workerId  sequence
 *  1bit 41bit timestamp (ms)                      10bit     12bit
 * </pre>
 *
 * <p>并发安全：workerId 在启动时从 workerId.datacenterId 配置读取（默认 0），
 * <p>单机场景使用 0/0；多机部署时通过 application.yml 配置不同 workerId。
 *
 * <p>ID 格式示例（无前缀）：{@code 1700000000000000001}
 *
 * <p>使用：
 * <pre>
 *   long id = IdGenerator.nextId();             // 纯数字 ID
 *   String s = IdGenerator.nextIdStr();        // 字符串
 *   String prefixed = "PO" + IdGenerator.nextIdStr();
 * </pre>
 *
 * @see <a href="https://github.com/twitter-archive/snowflake">Twitter Snowflake</a>
 */
@Component
public class IdGenerator {

    /** 起始时间戳（2026-01-01）— 后续 41 bit 还能用 ~69 年 */
    private static final long EPOCH = 1767225600000L;

    /** workerId 位数 */
    private static final long WORKER_ID_BITS = 10L;
    /** 序列号位数 */
    private static final long SEQUENCE_BITS = 12L;

    /** 最大 workerId = 1023 */
    private static final long MAX_WORKER_ID = ~(-1L << WORKER_ID_BITS);

    private static final long WORKER_ID_SHIFT = SEQUENCE_BITS;
    private static final long TIMESTAMP_SHIFT = SEQUENCE_BITS + WORKER_ID_BITS;

    /** 序列号掩码 = 4095 */
    private static final long SEQUENCE_MASK = ~(-1L << SEQUENCE_BITS);

    @Value("${app.id-generator.worker-id:0}")
    private volatile long workerId;

    @Value("${app.id-generator.datacenter-id:0}")
    private volatile long datacenterId;

    /** 上次生成 ID 的时间戳（毫秒） */
    private volatile long lastTimestamp = -1L;

    /** 同一毫秒内的序列号（0-4095） */
    private final AtomicLong sequence = new AtomicLong(0L);

    /** 单例 Spring Bean */
    private static volatile IdGenerator INSTANCE;

    public IdGenerator() {
        // 无参构造器（Spring 会用 @Value 注入 workerId / datacenterId）
    }

    /**
     * 获取当前实例（用于静态调用场景）
     */
    public static IdGenerator getInstance() {
        IdGenerator inst = INSTANCE;
        if (inst == null) {
            throw new IllegalStateException(
                "IdGenerator not initialized — Spring context not ready. " +
                "Use @Autowired injection or wait for application startup.");
        }
        return inst;
    }

    /**
     * Spring 启动后回调
     */
    @org.springframework.beans.factory.annotation.Autowired
    public void init() {
        // 校验 workerId / datacenterId 范围
        if (workerId > MAX_WORKER_ID || workerId < 0) {
            throw new IllegalArgumentException(
                "workerId must be in [0, " + MAX_WORKER_ID + "], got: " + workerId);
        }
        if (datacenterId > MAX_WORKER_ID || datacenterId < 0) {
            throw new IllegalArgumentException(
                "datacenterId must be in [0, " + MAX_WORKER_ID + "], got: " + datacenterId);
        }
        // 合并 datacenterId(5bit) + workerId(5bit) → 实际只用低 10bit
        // 此处保留双字段便于运维配置，但实际只取 workerId 部分（高 5bit = datacenterId 暂未启用）
        long effectiveWorkerId = (datacenterId << 5) | (workerId & 0x1F);
        this.workerId = effectiveWorkerId & MAX_WORKER_ID;

        INSTANCE = this;
    }

    /**
     * 生成下一个 ID（线程安全 — 经典 Twitter Snowflake 算法）
     */
    public synchronized long nextId() {
        long timestamp = currentTimeMillis();

        // 时钟回拨检测
        if (timestamp < lastTimestamp) {
            throw new RuntimeException(
                "Clock moved backwards. Refusing to generate id for " +
                (lastTimestamp - timestamp) + " milliseconds");
        }

        long seq;
        if (timestamp == lastTimestamp) {
            // 同一毫秒内 sequence 递增（从 1 开始）
            seq = (sequence.incrementAndGet()) & SEQUENCE_MASK;
            if (seq == 0) {
                // 序列号本毫秒用尽（4096 个），等待下一毫秒
                timestamp = waitNextMillis(lastTimestamp);
                seq = 0L;  // 新毫秒从 0 开始
                sequence.set(0L);
                lastTimestamp = timestamp;
            }
        } else {
            // 新的毫秒，序列号从 0 开始（首次 ID 使用 seq=0）
            seq = 0L;
            sequence.set(seq);
            lastTimestamp = timestamp;
        }

        return ((timestamp - EPOCH) << TIMESTAMP_SHIFT)
                | (workerId << WORKER_ID_SHIFT)
                | seq;
    }

    /** 字符串形式 */
    public String nextIdStr() {
        return String.valueOf(nextId());
    }

    /** 等待下一毫秒 */
    private long waitNextMillis(long lastTimestamp) {
        long timestamp = currentTimeMillis();
        while (timestamp <= lastTimestamp) {
            timestamp = currentTimeMillis();
        }
        return timestamp;
    }

    private long currentTimeMillis() {
        return System.currentTimeMillis();
    }

    // ============================================================
    // 静态便捷方法（需 Spring 容器已初始化）
    // ============================================================

    public static long nextLong() {
        return getInstance().nextId();
    }

    public static String nextString() {
        return getInstance().nextIdStr();
    }
}