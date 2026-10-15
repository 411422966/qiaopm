package com.meiyetec.util;

import jakarta.servlet.http.HttpServletRequest;

import java.util.UUID;

/**
 * traceId 工具：用于 R2.0-B（T-B02）与日志串联。
 *
 * <p>约定：</p>
 * <ul>
 *   <li>Header：`X-Trace-Id`</li>
 *   <li>MDC key：`traceId`</li>
 *   <li>生成顺序：header 透传 → 否则 UUID</li>
 * </ul>
 */
public final class TraceUtil {

    public static final String HEADER = "X-Trace-Id";
    public static final String MDC_KEY = "traceId";

    private TraceUtil() {}

    public static String getOrCreate(HttpServletRequest request) {
        if (request != null) {
            String t = request.getHeader(HEADER);
            if (t != null && !t.isEmpty()) return t;
        }
        return UUID.randomUUID().toString().replace("-", "");
    }

    public static String newTrace() {
        return UUID.randomUUID().toString().replace("-", "");
    }
}
