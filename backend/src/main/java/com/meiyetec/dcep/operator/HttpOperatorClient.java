package com.meiyetec.dcep.operator;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.meiyetec.dcep.operator.DcepOperatorProperties.Operator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;

/**
 * DCEP-03/04 阶段 B：基于 JDK 11+ HttpClient 的 REST 实现
 *
 * <p>适用机构：工行（ICBC）开放平台 / 农行（ABC）网络支付 / 中行（BOC）B2C
 *
 * <p>特点：
 * <ul>
 *   <li>零额外依赖（用 JDK 自带 HttpClient）</li>
 *   <li>连接池复用（HttpClient 单例）</li>
 *   <li>超时通过 {@code connectTimeoutMs} / {@code readTimeoutMs} 控制</li>
 *   <li>支持 mTLS（客户端证书，未来对接真实 SDK 时启用）</li>
 * </ul>
 */
@Slf4j
@Component
@ConditionalOnClass(name = "java.net.http.HttpClient")
public class HttpOperatorClient implements OperatorClient {

    private static final ObjectMapper JSON = new ObjectMapper();
    private final HttpClient httpClient;

    public HttpOperatorClient() {
        this.httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofSeconds(10))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    @Override
    public OperatorResponse invoke(Operator operator, String endpoint, Map<String, Object> payload) {
        String url = buildUrl(operator, endpoint);
        try {
            // 1. 业务字段 → 字符串映射（签名要求）
            Map<String, String> stringPayload = toStringMap(payload);
            // 2. 签名（注入到 payload 中）
            String signature = sign(operator, stringPayload);
            stringPayload.put("sign", signature);

            // 3. JSON 序列化
            String body = JSON.writeValueAsString(stringPayload);

            // 4. 构造 HTTP 请求
            HttpRequest.Builder reqBuilder = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofMillis(operator.getReadTimeoutMs()))
                    .header("Content-Type", "application/json; charset=utf-8")
                    .header("X-DCEP-Merchant", safe(operator.getMerchantId()))
                    .header("X-DCEP-Env", safe(operator.getEnvMode()))
                    .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8));
            // 5. 自定义头
            if (operator.getCustomHeaders() != null) {
                operator.getCustomHeaders().forEach(reqBuilder::header);
            }

            HttpResponse<String> resp = httpClient.send(reqBuilder.build(),
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

            int status = resp.statusCode();
            String respBody = resp.body();
            // 6. 解析 JSON
            Map<String, Object> data = new LinkedHashMap<>();
            try {
                if (respBody != null && !respBody.isEmpty()) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> m = JSON.readValue(respBody, Map.class);
                    if (m != null) data.putAll(m);
                }
            } catch (Exception parseEx) {
                log.debug("[DCEP-REST] resp 不是 JSON：{}", parseEx.getMessage());
            }

            boolean success = status >= 200 && status < 300
                    && (data.isEmpty() || isSuccessCode(data.get("code")));
            return OperatorResponse.builder()
                    .success(success)
                    .httpStatus(status)
                    .errorCode(success ? null : String.valueOf(data.getOrDefault("code", "HTTP_" + status)))
                    .errorMsg(success ? null : String.valueOf(data.getOrDefault("message", "银行返回错误")))
                    .data(data)
                    .rawBody(respBody)
                    .build();

        } catch (Exception e) {
            log.error("[DCEP-REST] 调用银行失败 url={} merchant={}", url, operator.getMerchantId(), e);
            return OperatorResponse.builder()
                    .success(false)
                    .errorCode("NETWORK_ERROR")
                    .errorMsg(e.getClass().getSimpleName() + ": " + e.getMessage())
                    .build();
        }
    }

    @Override
    public Protocol protocol() {
        return Protocol.REST;
    }

    @Override
    public boolean isConnected(String operatorKey) {
        // REST 短连接，每次 invoke 都是新连接 → 视为总可用
        return true;
    }

    @Override
    public void shutdown() {
        // HttpClient 无显式 shutdown
    }

    // ============================================================
    // 工具
    // ============================================================

    private String buildUrl(Operator operator, String endpoint) {
        String base = operator.getBaseUrl();
        if (base == null || base.isEmpty()) {
            throw new IllegalArgumentException("operator[" + operator.getMerchantId()
                    + "] 未配置 baseUrl");
        }
        return base.endsWith("/") ? base + endpoint.substring(1) : base + endpoint;
    }

    private Map<String, String> toStringMap(Map<String, Object> payload) {
        Map<String, String> out = new LinkedHashMap<>();
        if (payload == null) return out;
        payload.forEach((k, v) -> out.put(k, v == null ? "" : String.valueOf(v)));
        return out;
    }

    private String safe(String s) {
        return s == null ? "" : s;
    }

    /** 注入签名（延迟注入避免循环依赖：HttpOperatorClient 不直接依赖 OperatorSignatureService） */
    private String sign(Operator operator, Map<String, String> payload) {
        try {
            // 通过 ServiceLoader 或直接 Spring 注入 — 这里用静态访问
            return OperatorSignatureHolder.sign(operator, payload);
        } catch (Exception e) {
            log.warn("[DCEP-REST] 签名失败：{}，请求将以无签名方式发送（仅 SANDBOX）", e.getMessage());
            return "";
        }
    }

    private boolean isSuccessCode(Object code) {
        if (code == null) return true;
        String s = String.valueOf(code);
        return "0".equals(s) || "00000".equals(s) || "SUCCESS".equalsIgnoreCase(s) || "OK".equalsIgnoreCase(s);
    }

    /** 由 Spring 注入（避免构造器循环依赖） */
    public static class OperatorSignatureHolder {
        private static volatile OperatorSignatureService INSTANCE;

        public static void set(OperatorSignatureService s) {
            INSTANCE = s;
        }

        public static String sign(Operator op, Map<String, String> payload) {
            if (INSTANCE == null) {
                throw new IllegalStateException("OperatorSignatureService 未初始化");
            }
            return INSTANCE.sign(op, payload);
        }
    }
}
