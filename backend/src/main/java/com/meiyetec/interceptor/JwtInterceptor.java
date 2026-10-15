package com.meiyetec.interceptor;

import com.meiyetec.exception.BizCode;
import com.meiyetec.security.RbacContextLoader;
import com.meiyetec.security.TenantContext;
import com.meiyetec.security.TenantContextHolder;
import com.meiyetec.util.JwtUtil;
import com.meiyetec.util.ObjectMapperHolder;
import com.meiyetec.util.Result;
import com.meiyetec.util.TraceUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * JWT 解析 + 加载租户上下文（R2.0-A）。
 *
 * <p>职责：</p>
 * <ol>
 *   <li>校验 Authorization 是否带 Bearer；</li>
 *   <li>校验 Token 是否过期；</li>
 *   <li>通过 RbacContextLoader 加载用户、租户、权限码、数据范围；</li>
 *   <li>写入 TenantContextHolder + MDC traceId；</li>
 *   <li>拦截器链结束后，必须 try-finally clear() 上下文，避免线程复用串值。</li>
 * </ol>
 */
@Component
@RequiredArgsConstructor
public class JwtInterceptor implements HandlerInterceptor {

    private final JwtUtil jwtUtil;
    private final RbacContextLoader rbacContextLoader;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        // OPTIONS 跨域预检直接放行
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }

        String traceId = TraceUtil.getOrCreate(request);
        MDC.put(TraceUtil.MDC_KEY, traceId);
        response.setHeader(TraceUtil.HEADER, traceId);

        String authHeader = request.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            writeUnauthorized(response, BizCode.UNAUTHORIZED.message);
            return false;
        }

        String token = authHeader.replace("Bearer ", "").trim();
        try {
            if (jwtUtil.isTokenExpired(token)) {
                writeUnauthorized(response, BizCode.AUTH_TOKEN_EXPIRED.message);
                return false;
            }
            Long userId = jwtUtil.extractUserId(token);
            if (userId == null) {
                writeUnauthorized(response, BizCode.UNAUTHORIZED.message);
                return false;
            }
            TenantContext ctx = rbacContextLoader.load(userId);
            TenantContextHolder.set(ctx);
            return true;
        } catch (Exception e) {
            writeUnauthorized(response, e.getMessage());
            return false;
        }
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        TenantContextHolder.clear();
        MDC.remove(TraceUtil.MDC_KEY);
    }

    private static void writeUnauthorized(HttpServletResponse response, String message) throws java.io.IOException {
        response.setStatus(401);
        response.setContentType("application/json;charset=UTF-8");
        Result<Void> body = Result.error(BizCode.UNAUTHORIZED.code, message == null ? BizCode.UNAUTHORIZED.message : message);
        response.getWriter().write(ObjectMapperHolder.MAPPER.writeValueAsString(body));
    }
}
