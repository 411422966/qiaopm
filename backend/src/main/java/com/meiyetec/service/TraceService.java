package com.meiyetec.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.meiyetec.config.TraceContextHolder;
import com.meiyetec.entity.TraceSpan;
import com.meiyetec.mapper.TraceSpanMapper;
import com.meiyetec.security.TenantContextHolder;
import com.meiyetec.util.Result;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * P3.1 全链路追踪：追踪服务
 *
 * 功能：
 * 1. 异步记录 HTTP Span（每个请求一个 Span）
 * 2. 手动记录业务 Span（Service 层调用）
 * 3. 查询 Trace 链路（按 traceId 聚合）
 * 4. 链路可视化数据
 * 5. 链路染色（请求打标）
 * 6. 调用拓扑
 */
@Slf4j
@RestController
@RequestMapping("/trace")
@RequiredArgsConstructor
public class TraceService {

    private final TraceSpanMapper spanMapper;

    @Autowired
    private org.springframework.jdbc.core.JdbcTemplate jdbc;

    // 链路染色标签（内存缓存，生产建议 Redis）
    private static final Map<String, String> TRACE_TAGS = new ConcurrentHashMap<>();

    // Span ID 生成器
    private int spanCounter = 0;

    // ====================================================================
    // 1. HTTP Span 记录（由 TracingFilter 调用）
    // ====================================================================

    /**
     * 异步记录 HTTP 请求 Span（非事务：span 记录失败不影响调用方）
     */
    @Async
    public void recordHttpSpan(String traceId, String parentSpanId,
                                String method, String uri,
                                int httpStatus, String errorCode,
                                long durationMs,
                                String clientIp, String userAgent) {
        try {
            TraceSpan span = new TraceSpan();
            span.setTraceId(traceId);
            span.setSpanId(traceId + "-" + (++spanCounter));
            span.setParentSpanId(parentSpanId);
            span.setServiceName("meiyetec-api");
            span.setOperationName(method + " " + uri);
            span.setHttpMethod(method);
            span.setUri(uri);
            span.setStatus(errorCode == null ? "SUCCESS" : "ERROR");
            span.setHttpStatus(httpStatus);
            span.setErrorCode(errorCode);
            span.setStartAt(LocalDateTime.now().minusNanos(durationMs * 1_000_000));
            span.setDurationMs(durationMs);
            span.setTenantId(com.meiyetec.security.TenantContextHolder.get().getTenantId());
            span.setUserId(com.meiyetec.security.TenantContextHolder.get().getUserId());
            span.setClientIp(clientIp);
            span.setUserAgent(userAgent);

            // 染色标签
            String tag = TRACE_TAGS.get(traceId);
            if (tag != null) span.setTags(tag);

            spanMapper.insert(span);
        } catch (Exception e) {
            log.warn("[{}] 记录 Span 失败: {}", traceId, e.getMessage());
        }
    }

    // ====================================================================
    // 2. 业务 Span 记录（手动埋点）
    // ====================================================================

    /**
     * 手动记录业务 Span（如 Service 层调用）
     */
    @PostMapping("/span")
    public Result<TraceSpan> recordSpan(@RequestBody Map<String, Object> body) {
        String operationName = (String) body.get("operationName");
        String errorCode = (String) body.get("errorCode");
        String tag = (String) body.get("tag");

        String traceId = TraceContextHolder.getTraceId();
        if (traceId == null) {
            traceId = UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        }

        TraceSpan span = new TraceSpan();
        span.setTraceId(traceId);
        span.setSpanId(traceId + "-svc-" + (++spanCounter));
        span.setServiceName("meiyetec-api");
        span.setOperationName(operationName);
        span.setStatus(errorCode == null ? "SUCCESS" : "ERROR");
        span.setErrorCode(errorCode);
        span.setStartAt(LocalDateTime.now());
        span.setDurationMs(TraceContextHolder.getElapsedMs());
        span.setTenantId(com.meiyetec.security.TenantContextHolder.get().getTenantId());
        span.setUserId(com.meiyetec.security.TenantContextHolder.get().getUserId());
        span.setTags(tag);

        spanMapper.insert(span);
        return Result.success("已记录", span);
    }

    // ====================================================================
    // 3. 链路查询
    // ====================================================================

    /**
     * 按 Trace ID 查询完整链路
     */
    @GetMapping("/chain/{traceId}")
    public Result<List<TraceSpan>> getChain(@PathVariable String traceId) {
        List<TraceSpan> spans = spanMapper.selectList(
                new LambdaQueryWrapper<TraceSpan>()
                        .eq(TraceSpan::getTraceId, traceId)
                        .orderByAsc(TraceSpan::getStartAt));
        return Result.success(spans);
    }

    /**
     * 链路列表（分页，支持时间范围过滤）
     */
    @GetMapping("/chain/list")
    public Result<IPage<TraceSpan>> listChains(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "20") Integer pageSize,
            @RequestParam(required = false) String traceId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String operationName,
            @RequestParam(required = false) Long minDurationMs,
            @RequestParam(required = false) String startTime,
            @RequestParam(required = false) String endTime) {

        LambdaQueryWrapper<TraceSpan> qw = new LambdaQueryWrapper<>();
        qw.eq(traceId != null, TraceSpan::getTraceId, traceId);
        qw.eq(status != null, TraceSpan::getStatus, status);
        qw.like(operationName != null, TraceSpan::getOperationName, operationName);
        qw.ge(minDurationMs != null, TraceSpan::getDurationMs, minDurationMs);
        qw.ge(startTime != null, TraceSpan::getStartAt,
                startTime != null ? LocalDateTime.parse(startTime) : null);
        qw.le(endTime != null, TraceSpan::getStartAt,
                endTime != null ? LocalDateTime.parse(endTime) : null);
        qw.orderByDesc(TraceSpan::getStartAt);

        IPage<TraceSpan> result = spanMapper.selectPage(
                new Page<>(page, pageSize), qw);
        return Result.success(result);
    }

    // ====================================================================
    // 4. 链路可视化数据（树形结构）
    // ====================================================================

    /**
     * 生成链路树（用于前端可视化）
     */
    @GetMapping("/visual/{traceId}")
    public Result<Map<String, Object>> visualize(@PathVariable String traceId) {
        List<TraceSpan> spans = spanMapper.selectList(
                new LambdaQueryWrapper<TraceSpan>()
                        .eq(TraceSpan::getTraceId, traceId)
                        .orderByAsc(TraceSpan::getStartAt));

        if (spans.isEmpty()) {
            return Result.error(com.meiyetec.exception.BizCode.NOT_FOUND, "链路不存在");
        }

        // 构建树
        List<Map<String, Object>> nodes = new ArrayList<>();
        for (TraceSpan s : spans) {
            Map<String, Object> node = new LinkedHashMap<>();
            node.put("spanId", s.getSpanId());
            node.put("parentSpanId", s.getParentSpanId());
            node.put("serviceName", s.getServiceName());
            node.put("operationName", s.getOperationName());
            node.put("status", s.getStatus());
            node.put("durationMs", s.getDurationMs());
            node.put("startAt", s.getStartAt());
            node.put("httpStatus", s.getHttpStatus());
            node.put("errorCode", s.getErrorCode());
            node.put("clientIp", s.getClientIp());
            node.put("tags", s.getTags());
            nodes.add(node);
        }

        TraceSpan first = spans.get(0);
        TraceSpan last = spans.get(spans.size() - 1);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("traceId", traceId);
        data.put("totalDurationMs", last.getDurationMs());
        data.put("spanCount", spans.size());
        data.put("startAt", first.getStartAt());
        data.put("endAt", last.getStartAt());
        data.put("nodes", nodes);

        return Result.success(data);
    }

    // ====================================================================
    // 5. 链路染色（打标）
    // ====================================================================

    /**
     * 为指定 Trace 打标签（如 "debug" / "v2.1" / "internal"）
     */
    @PostMapping("/tag/{traceId}")
    public Result<Void> tagTrace(@PathVariable String traceId,
                                  @RequestBody Map<String, Object> body) {
        String tag = (String) body.get("tag");
        if (tag == null) return Result.error(com.meiyetec.exception.BizCode.BAD_REQUEST, "tag 必填");
        TRACE_TAGS.put(traceId, tag);
        return Result.success("已标记", null);
    }

    // ====================================================================
    // 6. 调用拓扑
    // ====================================================================

    /**
     * 服务调用拓扑统计
     */
    @GetMapping("/topology")
    public Result<List<Map<String, Object>>> topology(
            @RequestParam(defaultValue = "7") Integer days) {
        Long tenantId = TenantContextHolder.get().getTenantId();
        LocalDateTime since = LocalDateTime.now().minusDays(days);

        List<Map<String, Object>> rows = new ArrayList<>();
        // 按 operationName 分组统计 QPS / 平均耗时 / 错误率
        String sql = """
            SELECT operation_name,
                   COUNT(*) AS total_count,
                   AVG(duration_ms) AS avg_duration_ms,
                   MAX(duration_ms) AS max_duration_ms,
                   SUM(CASE WHEN status = 'ERROR' THEN 1 ELSE 0 END) AS error_count,
                   SUM(CASE WHEN status = 'ERROR' THEN 1 ELSE 0 END) * 100.0 / COUNT(*) AS error_rate_pct
            FROM trace_span
            WHERE tenant_id = ? AND start_at >= ?
            GROUP BY operation_name
            ORDER BY total_count DESC
            LIMIT 50
            """;
        try {
            rows = new java.util.ArrayList<>(jdbc.queryForList(sql, tenantId, since));
        } catch (Exception e) {
            log.warn("拓扑统计查询失败: {}", e.getMessage());
        }
        return Result.success(rows);
    }

    // ====================================================================
    // 7. 慢链路 TOP
    // ====================================================================

    /**
     * 慢链路 TOP
     */
    @GetMapping("/slow/top")
    public Result<List<TraceSpan>> slowTop(
            @RequestParam(defaultValue = "10") Integer limit,
            @RequestParam(defaultValue = "500") Long thresholdMs) {
        Long tenantId = TenantContextHolder.get().getTenantId();
        return Result.success(spanMapper.selectList(
                new LambdaQueryWrapper<TraceSpan>()
                        .eq(TraceSpan::getTenantId, tenantId)
                        .ge(TraceSpan::getDurationMs, thresholdMs)
                        .orderByAsc(TraceSpan::getDurationMs)
                        .last("LIMIT " + limit)));
    }

    // ====================================================================
    // 8. 错误链路 TOP
    // ====================================================================

    @GetMapping("/error/top")
    public Result<List<TraceSpan>> errorTop(
            @RequestParam(defaultValue = "10") Integer limit) {
        Long tenantId = TenantContextHolder.get().getTenantId();
        return Result.success(spanMapper.selectList(
                new LambdaQueryWrapper<TraceSpan>()
                        .eq(TraceSpan::getTenantId, tenantId)
                        .eq(TraceSpan::getStatus, "ERROR")
                        .orderByDesc(TraceSpan::getCreatedAt)
                        .last("LIMIT " + limit)));
    }
}
