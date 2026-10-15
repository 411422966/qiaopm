package com.meiyetec.log;

import com.meiyetec.security.TenantContext;
import com.meiyetec.security.TenantContextHolder;

/**
 * 操作日志上下文：TenantContext 的轻量代理。
 * 解析规则统一在 JwtInterceptor，LogContext 仅供 LogAspect 读取。
 */
public class LogContext {

    private LogContext() {}

    public static Long getUserId() {
        return TenantContextHolder.get().getUserId();
    }

    public static String getUsername() {
        return TenantContextHolder.get().getUsername();
    }

    public static Long getTenantId() {
        return TenantContextHolder.get().getTenantId();
    }

    public static void clear() {
        TenantContextHolder.clear();
    }

    public static TenantContext current() {
        return TenantContextHolder.get();
    }
}
