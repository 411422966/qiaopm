package com.meiyetec.security;

import com.meiyetec.exception.BizCode;
import com.meiyetec.exception.BusinessException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 数据范围前置拦截：
 * 1. 跨租户：ctx.tenantId != param.tenantId 且 not ALL → 403 TENANT_DENIED
 * 2. 跨门店：ctx.storeIds 为空 / 不包含 → 403 STORE_DENIED
 *
 * <p>采集 storeId / tenantId 的来源（按优先级）：
 * <ol>
 *   <li>Header（X-Store-Id / X-Tenant-Id）</li>
 *   <li>Query string（?storeId=1）</li>
 *   <li>Form 表单</li>
 *   <li>JSON body（需 LogContextFilter 用 ContentCachingRequestWrapper 包裹 request）</li>
 * </ol>
 * </p>
 */
@Component
public class DataScopeInterceptor implements HandlerInterceptor {

    // P1-SCOPE-001: 白名单路径不做数据范围校验
    private static final Set<String> WHITELIST = Set.of(
        "/auth/login",
        "/auth/refresh",
        "/sys/log/export",
        "/report/export",
        "/report/download",
        "/booking/export",
        "/commission/salary/export",
        "/commission/settlement/export",
        "/market/coupon/export",
        "/stock/export"
    );

    /** 宽松 JSON 字段匹配：支持 "storeId" / "store_id" / "storeId ":123 / "storeIds":[1,2] */
    private static final Pattern STORE_ID_P = Pattern.compile(
            "\"(?:store[_-]?id|storeId)\"\\s*:\\s*\"?(-?\\d+)\"?", Pattern.CASE_INSENSITIVE);
    private static final Pattern TENANT_ID_P = Pattern.compile(
            "\"(?:tenant[_-]?id|tenantId)\"\\s*:\\s*\"?(-?\\d+)\"?", Pattern.CASE_INSENSITIVE);

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String path = request.getRequestURI();
        // 白名单路径直接放行
        if (WHITELIST.contains(path)) return true;

        TenantContext ctx = TenantContextHolder.get();
        if (ctx.getUserId() == null) return true;

        Long tid = extractTenantId(request);
        if (tid != null && ctx.getTenantId() != null
                && !ctx.getTenantId().equals(tid)
                && !"ALL".equals(ctx.getDataScope())) {
            throw new BusinessException(BizCode.TENANT_DENIED);
        }

        Long sid = extractStoreId(request);
        if (sid != null && !ctx.canAccessStore(sid)) {
            throw new BusinessException(BizCode.STORE_DENIED);
        }

        return true;
    }

    // ===== 取值辅助 =====

    private Long extractTenantId(HttpServletRequest req) {
        // 1. Header
        String v = req.getHeader("X-Tenant-Id");
        if (v == null || v.isEmpty()) v = req.getHeader("x-tenant-id");
        if (v != null && !v.isEmpty()) {
            Long n = parseLong(v);
            if (n != null) return n;
        }
        // 2. Query
        v = req.getParameter("tenantId");
        if (v == null || v.isEmpty()) v = req.getParameter("tenant_id");
        if (v != null && !v.isEmpty()) {
            Long n = parseLong(v);
            if (n != null) return n;
        }
        // 3. JSON body
        return matchLongFromBody(req, TENANT_ID_P);
    }

    private Long extractStoreId(HttpServletRequest req) {
        String v = req.getHeader("X-Store-Id");
        if (v == null || v.isEmpty()) v = req.getHeader("x-store-id");
        if (v != null && !v.isEmpty()) {
            Long n = parseLong(v);
            if (n != null) return n;
        }
        v = req.getParameter("storeId");
        if (v == null || v.isEmpty()) v = req.getParameter("store_id");
        if (v != null && !v.isEmpty()) {
            Long n = parseLong(v);
            if (n != null) return n;
        }
        return matchLongFromBody(req, STORE_ID_P);
    }

    private Long parseLong(String s) {
        try { return Long.valueOf(s); } catch (NumberFormatException e) { return null; }
    }

    /** 从 request body（已被 LogContextFilter 用 CachedBodyRequestWrapper 包裹）匹配字段 */
    private Long matchLongFromBody(HttpServletRequest req, Pattern p) {
        if (!(req instanceof CachedBodyRequestWrapper wrapper)) return null;
        byte[] body = wrapper.getContentAsByteArray();
        if (body == null || body.length == 0) return null;
        // 只解析 JSON 类请求体，避免对二进制/大文件做正则扫描
        String ct = req.getContentType();
        if (ct != null && !ct.toLowerCase().contains("json")) return null;
        if (body.length > 256 * 1024) return null;   // >256KB 不解析，提示业务按 header/query 传参
        String s = new String(body, StandardCharsets.UTF_8);
        Matcher m = p.matcher(s);
        return m.find() ? Long.valueOf(m.group(1)) : null;
    }

    /**
     * Service 层兜底：当 store_ids 列表为空时返回 false，业务抛 STORE_DENIED。
     */
    public static boolean canAccess(TenantContext ctx, Long storeId) {
        if (ctx.getUserId() == null) return true;
        Set<Long> ids = ctx.getStoreIds();
        if (ids == null) return false;
        if (ids.contains(-1L)) return true;
        return storeId == null || ids.contains(storeId);
    }
}