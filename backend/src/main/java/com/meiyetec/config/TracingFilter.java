package com.meiyetec.config;

import com.meiyetec.service.TraceService;
import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.UUID;

/**
 * P3.1 全链路追踪：HTTP 过滤器
 *
 * 功能：
 * 1. 生成或传播 Trace ID（X-Trace-ID）
 * 2. 注入 Response Header
 * 3. 设置线程上下文 TraceContextHolder
 * 4. 记录请求开始时间（用于耗时计算）
 */
@Slf4j
@Component
@Order(1)
public class TracingFilter implements Filter {

    public static final String TRACE_ID_HEADER = "X-Trace-ID";

    private final TraceService traceService;

    public TracingFilter(TraceService traceService) {
        this.traceService = traceService;
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        HttpServletRequest req = (HttpServletRequest) request;
        HttpServletResponse resp = (HttpServletResponse) response;

        // 1. 提取或生成 Trace ID
        String traceId = req.getHeader(TRACE_ID_HEADER);
        if (traceId == null || traceId.isBlank()) {
            traceId = UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        }

        // 2. 记录开始时间
        long startNano = System.nanoTime();

        // 3. 注入 Response Header
        resp.setHeader(TRACE_ID_HEADER, traceId);

        // 4. 设置线程上下文
        TraceContextHolder.setTraceId(traceId);
        TraceContextHolder.setStartNano(startNano);

        // 5. 缓存信息用于 Span
        String parentSpanId = req.getHeader("X-Parent-Span-ID");
        String method = req.getMethod();
        String uri = req.getRequestURI();
        String clientIp = getClientIp(req);
        String userAgent = req.getHeader("User-Agent");

        try {
            if (log.isDebugEnabled()) {
                log.debug("[{}] --> {} {}", traceId, method, uri);
            }
            chain.doFilter(request, response);
        } finally {
            // 6. 记录 Span
            long elapsedMs = (System.nanoTime() - startNano) / 1_000_000;
            int status = resp.getStatus();
            if (log.isDebugEnabled()) {
                log.debug("[{}] <-- {} {} => {} ({}ms)", traceId, method, uri, status, elapsedMs);
            }
            // 异步记录，不阻塞响应
            traceService.recordHttpSpan(traceId, parentSpanId, method, uri,
                    status, null, elapsedMs, clientIp, userAgent);
            TraceContextHolder.clear();
        }
    }

    private String getClientIp(HttpServletRequest req) {
        String ip = req.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = req.getHeader("X-Real-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = req.getRemoteAddr();
        }
        return ip;
    }
}
