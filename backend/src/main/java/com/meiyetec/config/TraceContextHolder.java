package com.meiyetec.config;

import lombok.extern.slf4j.Slf4j;

/**
 * P3.1 全链路追踪：线程上下文持有者
 *
 * 存储当前请求的 Trace ID 和开始时间，
 * 供 Service 层在任何位置获取并写入日志/响应。
 */
@Slf4j
public class TraceContextHolder {

    private static final ThreadLocal<TraceInfo> CONTEXT = new ThreadLocal<>();

    public static void setTraceId(String traceId) {
        CONTEXT.set(new TraceInfo(traceId, System.nanoTime()));
    }

    public static void setStartNano(long startNano) {
        TraceInfo existing = CONTEXT.get();
        if (existing != null) {
            CONTEXT.set(new TraceInfo(existing.traceId, startNano));
        } else {
            CONTEXT.set(new TraceInfo(null, startNano));
        }
    }

    public static String getTraceId() {
        TraceInfo info = CONTEXT.get();
        return info == null ? null : info.traceId;
    }

    public static long getStartNano() {
        TraceInfo info = CONTEXT.get();
        return info == null ? 0 : info.startNano;
    }

    public static long getElapsedMs() {
        if (CONTEXT.get() == null) return 0;
        return (System.nanoTime() - CONTEXT.get().startNano) / 1_000_000;
    }

    public static void clear() {
        CONTEXT.remove();
    }

    private record TraceInfo(String traceId, long startNano) {}
}
