package com.meiyetec.config;

import com.meiyetec.security.TenantContextHolder;
import com.meiyetec.util.TraceUtil;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * R2.0-B T-B02：traceId 过滤器 + MDC 串联。
 * <p>所有请求都会带上 X-Trace-Id，响应头透回；MDC 中保存 traceId，Logback 日志格式可读取。</p>
 * <p>顺序：最早执行；JWT 拦截器之后只补 dataScope / 权限码，不重置 traceId。</p>
 */
@Component
@Order(Integer.MIN_VALUE + 10)
public class TraceFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String traceId = TraceUtil.getOrCreate(request);
        try {
            MDC.put(TraceUtil.MDC_KEY, traceId);
            response.setHeader(TraceUtil.HEADER, traceId);
            chain.doFilter(request, response);
        } finally {
            MDC.remove(TraceUtil.MDC_KEY);
            TenantContextHolder.clear();
        }
    }
}
