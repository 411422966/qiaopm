package com.meiyetec.service;

import com.meiyetec.security.TenantContextHolder;
import com.meiyetec.util.Result;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.concurrent.*;

/**
 * R3.0-H P2.6：性能压测 / QPS 评估
 *
 * <p>提供：
 * <ul>
 *   <li>端点 QPS 评估（单线程 + 多线程）</li>
 *   <li>延迟分布（P50 / P95 / P99 / MAX）</li>
 *   <li>数据生成（用于压测）</li>
 *   <li>基线对比</li>
 * </ul>
 *
 * <p>生产应替换为 JMeter / Gatling / wrk / ab。
 */
@Slf4j
@RestController
@RequestMapping("/perf")
@RequiredArgsConstructor
public class PerformanceService {

    @Autowired
    private JdbcTemplate jdbc;

    // ====================================================================
    // 1. 端点 QPS 评估
    // ====================================================================

    /**
     * 对指定 SQL 端点做并发压测，统计 QPS / 延迟分布
     */
    @PostMapping("/qps/test")
    public Result<Map<String, Object>> qpsTest(@RequestBody Map<String, Object> body) {
        Long tenantId = TenantContextHolder.get().getTenantId();
        Integer threads = body.get("threads") == null ? 10 : ((Number) body.get("threads")).intValue();
        Integer iterations = body.get("iterations") == null ? 100 : ((Number) body.get("iterations")).intValue();
        String sql = (String) body.get("sql");
        if (sql == null) return Result.error(com.meiyetec.exception.BizCode.BAD_REQUEST, "sql 必填");

        ExecutorService pool = Executors.newFixedThreadPool(threads);
        List<Future<Long>> futures = new ArrayList<>();
        long start = System.currentTimeMillis();

        for (int t = 0; t < threads; t++) {
            for (int i = 0; i < iterations; i++) {
                futures.add(pool.submit(() -> {
                    long s = System.nanoTime();
                    try {
                        jdbc.queryForList(sql);
                    } catch (Exception e) {
                        log.debug("压测 SQL 异常：{}", e.getMessage());
                    }
                    return System.nanoTime() - s;
                }));
            }
        }

        List<Long> latencies = new ArrayList<>();
        for (Future<Long> f : futures) {
            try {
                latencies.add(f.get(5, TimeUnit.SECONDS));
            } catch (Exception ignored) {
            }
        }
        pool.shutdown();
        long elapsedMs = System.currentTimeMillis() - start;

        Collections.sort(latencies);
        int total = latencies.size();
        double qps = (double) total * 1000 / Math.max(1, elapsedMs);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("threads", threads);
        data.put("iterationsPerThread", iterations);
        data.put("totalRequests", total);
        data.put("elapsedMs", elapsedMs);
        data.put("qps", Math.round(qps * 100) / 100.0);
        data.put("latency", Map.of(
                "p50_ms", toMs(latencies.get(total / 2)),
                "p95_ms", toMs(latencies.get((int) (total * 0.95))),
                "p99_ms", toMs(latencies.get((int) (total * 0.99))),
                "max_ms", toMs(latencies.get(total - 1)),
                "min_ms", toMs(latencies.get(0))));
        return Result.success(data);
    }

    private static double toMs(long nanos) {
        return Math.round(nanos / 1000.0 / 1000.0 * 100) / 100.0;
    }

    // ====================================================================
    // 2. 压测数据生成
    // ====================================================================

    @PostMapping("/data/generate")
    public Result<Map<String, Object>> generateTestData(@RequestBody Map<String, Object> body) {
        Integer count = body.get("count") == null ? 1000 : ((Number) body.get("count")).intValue();
        String table = (String) body.getOrDefault("table", "crm_member");
        if (count > 100000) return Result.error(com.meiyetec.exception.BizCode.BAD_REQUEST, "单次最多生成 10 万");

        Random rng = new Random();
        String[] names = {"张三", "李四", "王五", "赵六", "钱七", "孙八", "周九", "吴十"};
        int created = 0;
        try {
            for (int i = 0; i < count; i++) {
                String name = names[rng.nextInt(names.length)] + i;
                String phone = "138" + String.format("%08d", rng.nextInt(100000000));
                jdbc.update("INSERT INTO crm_member (tenant_id, name, phone, level_code, points, register_date, deleted) " +
                                "VALUES (?, ?, ?, ?, ?, NOW(), 0)",
                        1L, name, phone, "NORMAL", rng.nextInt(10000));
                created++;
                if (created % 1000 == 0) {
                    log.info("生成压测数据 {} / {}", created, count);
                }
            }
        } catch (Exception e) {
            return Result.error(com.meiyetec.exception.BizCode.SYSTEM_ERROR, "生成失败：表不存在或权限不足 " + e.getMessage());
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("table", table);
        data.put("count", created);
        return Result.success("已生成", data);
    }

    // ====================================================================
    // 3. 慢查询 TOP-N
    // ====================================================================

    @GetMapping("/slow/top")
    public Result<List<Map<String, Object>>> slowTop(
            @RequestParam(defaultValue = "1000") Long thresholdMs) {
        Long tenantId = TenantContextHolder.get().getTenantId();
        try {
            List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT sql_hash, COUNT(*) AS occurrences, " +
                "       AVG(execution_ms) AS avg_ms, MAX(execution_ms) AS max_ms " +
                "FROM cloud_slow_query " +
                "WHERE tenant_id = ? AND execution_ms >= ? " +
                "GROUP BY sql_hash ORDER BY avg_ms DESC LIMIT 10",
                tenantId, thresholdMs);
            return Result.success(rows);
        } catch (Exception e) {
            return Result.success(List.of());
        }
    }

    // ====================================================================
    // 4. 容量评估
    // ====================================================================

    @GetMapping("/capacity")
    public Result<Map<String, Object>> capacity() {
        Map<String, Object> data = new LinkedHashMap<>();
        Runtime r = Runtime.getRuntime();
        data.put("totalMemory", r.totalMemory() / (1024 * 1024) + "MB");
        data.put("freeMemory", r.freeMemory() / (1024 * 1024) + "MB");
        data.put("maxMemory", r.maxMemory() / (1024 * 1024) + "MB");
        data.put("availableProcessors", r.availableProcessors());
        return Result.success(data);
    }
}