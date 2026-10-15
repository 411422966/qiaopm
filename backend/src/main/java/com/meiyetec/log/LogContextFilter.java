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
            // 用 CachedBodyRequestWrapper 把 body 全量读到内存，并允许重复读取。
            // DataScopeInterceptor 需要从 JSON body 读 storeId/tenantId 做越权拦截。
            // ContentCachingRequestWrapper 不能用：它在 controller 读 body 前返回空数组。
            HttpServletRequest cached = (request instanceof CachedBodyRequestWrapper)
                    ? request
                    : new CachedBodyRequestWrapper(request);

            String auth = cached.getHeader("Authorization");
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
}
