package com.meiyetec.config;

import com.meiyetec.util.TraceUtil;
import com.meiyetec.util.Result;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.MDC;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpResponse;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

import java.util.Objects;

/**
 * R2.0-B T-B02：响应体 / 错误体都带 traceId 字段。
 * <p>对 @RestController 返回的 Result<T> 统一注入 traceId 与 timestamp。</p>
 */
@RestControllerAdvice(basePackages = "com.meiyetec")
public class TraceResponseAdvice implements ResponseBodyAdvice<Object> {

    @Override
    public boolean supports(MethodParameter returnType, Class<? extends HttpMessageConverter<?>> converterType) {
        return true;
    }

    @Override
    public Object beforeBodyWrite(Object body, MethodParameter returnType, MediaType selectedContentType,
                                   Class<? extends HttpMessageConverter<?>> selectedConverterType,
                                   ServerHttpRequest request, ServerHttpResponse response) {
        if (body == null) return null;
        String traceId = MDC.get(TraceUtil.MDC_KEY);
        if (traceId == null) traceId = TraceUtil.newTrace();
        if (body instanceof Result<?> r) {
            if (r.getTraceId() == null) r.setTraceId(traceId);
            if (r.getTimestamp() == null) r.setTimestamp(java.time.LocalDateTime.now().toString());
        }
        if (response instanceof ServletServerHttpResponse ssr) {
            Objects.requireNonNull(ssr.getServletResponse()).setHeader(TraceUtil.HEADER, traceId);
        }
        return body;
    }
}
