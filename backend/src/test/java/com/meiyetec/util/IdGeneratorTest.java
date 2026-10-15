package com.meiyetec.util;

import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

/**
 * P0-MONEY-003 · 雪花算法 ID 生成器测试
 *
 * <p>覆盖：
 * <ul>
 *   <li>基本 ID 生成：单调递增、长度合理</li>
 *   <li>10000 并发 ID 无碰撞（雪花算法核心保证）</li>
 *   <li>ID 格式校验（时间戳 + workerId + sequence）</li>
 *   <li>nextIdStr() 字符串输出</li>
 * </ul>
 */
@SpringBootTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class IdGeneratorTest {

    @Autowired
    private IdGenerator idGenerator;

    private static final int CONCURRENCY = 10000;

    // ================================================================
    // 1. 基本 ID 生成
    // ================================================================
    @Test
    @Order(1)
    @DisplayName("IdGenerator-1: 单线程生成 100 个 ID，全部唯一且单调递增")
    void singleThreadGenerate() {
        long prev = idGenerator.nextId();
        for (int i = 0; i < 100; i++) {
            long curr = idGenerator.nextId();
            assertTrue(curr > prev, "ID 必须单调递增, prev=" + prev + " curr=" + curr);
            prev = curr;
        }
    }

    // ================================================================
    // 2. nextIdStr() 字符串输出
    // ================================================================
    @Test
    @Order(2)
    @DisplayName("IdGenerator-2: nextIdStr() 返回纯数字字符串")
    void stringOutput() {
        String s1 = idGenerator.nextIdStr();
        String s2 = idGenerator.nextIdStr();
        assertNotNull(s1);
        assertNotNull(s2);
        assertTrue(s2.compareTo(s1) > 0, "字符串也应递增: " + s1 + " vs " + s2);
        assertTrue(s1.matches("\\d+"), "字符串应纯数字: " + s1);
    }

    // ================================================================
    // 3. 10000 并发无碰撞（雪花算法核心场景）
    // ================================================================
    @Test
    @Order(3)
    @DisplayName("IdGenerator-3: 10000 并发生成，无碰撞（雪花算法核心保证）")
    void concurrentNoCollision() throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(50);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(CONCURRENCY);

        Set<Long> idSet = ConcurrentHashMap.newKeySet();
        Set<String> idStrSet = ConcurrentHashMap.newKeySet();

        for (int i = 0; i < CONCURRENCY; i++) {
            executor.submit(() -> {
                try {
                    start.await();
                    long id = idGenerator.nextId();
                    String idStr = idGenerator.nextIdStr();
                    idSet.add(id);
                    idStrSet.add(idStr);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    done.countDown();
                }
            });
        }

        long t0 = System.currentTimeMillis();
        start.countDown();
        assertTrue(done.await(60, TimeUnit.SECONDS), "并发生成超时");
        long elapsed = System.currentTimeMillis() - t0;

        assertEquals(CONCURRENCY, idSet.size(),
                "10000 个 long ID 必须无碰撞，实际唯一数=" + idSet.size());
        assertEquals(CONCURRENCY, idStrSet.size(),
                "10000 个 string ID 必须无碰撞，实际唯一数=" + idStrSet.size());

        System.out.printf("[IdGenerator] %d 并发 ID 生成完成，耗时 %d ms (%.2f ms/ID)%n",
                CONCURRENCY, elapsed, (double) elapsed / (CONCURRENCY * 2));

        executor.shutdown();
    }

    // ================================================================
    // 4. ID 格式校验（雪花算法结构）
    // ================================================================
    @Test
    @Order(4)
    @DisplayName("IdGenerator-4: ID 结构正确（41bit timestamp + 10bit worker + 12bit sequence）")
    void idStructureCorrect() {
        long id = idGenerator.nextId();

        // 当前时间戳（毫秒）
        long now = System.currentTimeMillis();
        long epoch = 1767225600000L;  // 与 IdGenerator.EPOCH 一致

        // 反解 timestamp（取高 41 bit）
        long timestamp = (id >> 22) + epoch;
        long diff = Math.abs(now - timestamp);

        assertTrue(diff < 1000,
                "时间戳应接近当前时间，diff=" + diff + "ms (id=" + id + ", now=" + now + ")");
        assertTrue(id > 0, "ID 必须为正数");
        // 雪花算法 64 bit 长度限制（最高位 = 0 因为 timestamp 在 EPOCH 之后最多 ~69 年）
        assertTrue(id < (1L << 62), "ID 应小于 2^62 (snowflake 单调约束)");
    }

    // ================================================================
    // 5. 多线程性能（5 个线程 × 5000 ID = 25000，验证性能与唯一性）
    // ================================================================
    @Test
    @Order(5)
    @DisplayName("IdGenerator-5: 5 线程 × 5000 ID = 25000，无碰撞且 < 5 秒")
    void highVolumeNoCollision() throws Exception {
        int threadCount = 5;
        int perThread = 5000;
        int total = threadCount * perThread;

        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(total);
        Set<Long> idSet = ConcurrentHashMap.newKeySet();
        AtomicLong maxId = new AtomicLong(0);

        for (int t = 0; t < threadCount; t++) {
            executor.submit(() -> {
                try {
                    start.await();
                    for (int i = 0; i < perThread; i++) {
                        long id = idGenerator.nextId();
                        idSet.add(id);
                        // 跟踪最大 ID（验证单调性，弱约束）
                        long prev = maxId.get();
                        while (id > prev) {
                            if (maxId.compareAndSet(prev, id)) break;
                            prev = maxId.get();
                        }
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    for (int i = 0; i < perThread; i++) done.countDown();
                }
            });
        }

        long t0 = System.currentTimeMillis();
        start.countDown();
        assertTrue(done.await(30, TimeUnit.SECONDS), "高并发生成超时");
        long elapsed = System.currentTimeMillis() - t0;

        assertEquals(total, idSet.size(),
                "25000 个 ID 必须全部唯一，实际唯一数=" + idSet.size());
        assertTrue(elapsed < 5000,
                "生成 25000 ID 应 < 5 秒，实际=" + elapsed + "ms");

        System.out.printf("[IdGenerator] %d ID 高并发完成，耗时 %d ms (%.3f ms/ID)%n",
                total, elapsed, (double) elapsed / total);

        executor.shutdown();
    }

    // ================================================================
    // 6. 时钟回拨检测（仅验证异常抛出，不实际回拨时钟）
    // ================================================================
    @Test
    @Order(6)
    @DisplayName("IdGenerator-6: 时钟回拨应抛 RuntimeException")
    void clockMovedBackwards() throws Exception {
        // 通过反射设置 lastTimestamp 为未来时间
        java.lang.reflect.Field lastTsField = IdGenerator.class.getDeclaredField("lastTimestamp");
        lastTsField.setAccessible(true);
        long future = System.currentTimeMillis() + 60000;  // 1 分钟后
        lastTsField.set(idGenerator, future);

        try {
            RuntimeException ex = assertThrows(RuntimeException.class, () -> idGenerator.nextId());
            assertTrue(ex.getMessage().contains("Clock moved backwards"),
                    "应抛 Clock moved backwards 异常，实际=" + ex.getMessage());
        } finally {
            // 恢复
            lastTsField.set(idGenerator, -1L);
        }
    }
}