package com.meiyetec.log;

import com.meiyetec.security.CachedBodyRequestWrapper;
import com.meiyetec.security.TenantContext;
import com.meiyetec.security.TenantContextHolder;
import com.meiyetec.util.JwtUtil;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Locale;

/**
 * 在 JwtInterceptor 之前先用 header 粗解析一次，写一份简化版 TenantContext 到 ThreadLocal，
 * 让 LogAspect 在请求早期就能拿到 userId/username。
 * <p>JwtInterceptor 解析完成后会用完整 RbacContext 覆盖本值（包含权限码与数据范围）。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LogContextFilter extends OncePerRequestFilter {

    private final JwtUtil jwtUtil;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        try {
            // 只有「可缓存」的请求才包装成 CachedBodyRequestWrapper：
            // 1) multipart 上传请求绝不能缓存 —— 缓存会在构造时把输入流读完并关闭，
            //    导致后续 Multipart 解析器（getParts）拿不到流 → 500 MultipartException；
            // 2) Content-Length 未知（chunked）或超出上限时不缓存 —— 截断缓存会损坏请求体。
            //    这两类请求 DataScopeInterceptor 本来就取不到 body 里的 tenantId/storeId，
            //    仍会走 header / query 参数校验，租户隔离强度不变。
            HttpServletRequest cached = request;
            if (isCacheable(request)) {
                // 用 CachedBodyRequestWrapper 把 body 全量读到内存，并允许重复读取。
                // DataScopeInterceptor 需要从 JSON body 读 storeId/tenantId 做越权拦截。
                // ContentCachingRequestWrapper 不能用：它在 controller 读 body 前返回空数组。
                cached = new CachedBodyRequestWrapper(request);
            }

            String auth = request.getHeader("Authorization");
            if (auth != null && auth.startsWith("Bearer ")) {
                String token = auth.substring(7);
                try {
                    Long uid = jwtUtil.extractUserId(token);
                    String username = jwtUtil.extractUsername(token);
                    TenantContext ctx = TenantContextHolder.get();
                    if (uid != null) ctx.setUserId(uid);
                    if (username != null) ctx.setUsername(username);
                } catch (JwtException | IllegalArgumentException ignored) {
                }
            }
            chain.doFilter(cached, response);
        } finally {
            // 不在这里 clear；JwtInterceptor.afterCompletion 统一清理
        }
    }

    /** 是否允许把请求体缓存下来（可重复读取） */
    private static boolean isCacheable(HttpServletRequest request) {
        if (request instanceof CachedBodyRequestWrapper) return false;  // 已包装过，无需重复包装
        if (isMultipart(request)) return false;                          // 上传请求的流不能被提前消费
        return CachedBodyRequestWrapper.isCacheable(request);
    }

    /** 是否是 multipart 上传请求（Content-Type: multipart/form-data 等） */
    private static boolean isMultipart(HttpServletRequest request) {
        String contentType = request.getContentType();
        return contentType != null && contentType.toLowerCase(Locale.ROOT).startsWith("multipart/");
    }
}
