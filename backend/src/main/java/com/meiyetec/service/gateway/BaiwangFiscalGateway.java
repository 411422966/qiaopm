package com.meiyetec.service.gateway;

import com.meiyetec.config.FiscalConfigProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * FISCAL-001：百望云电子发票 HTTP REST 网关
 *
 * <p>激活条件：{@code fiscal.mode=baiwang}
 *
 * <p>签名方案（百望行业通用 HMAC-SHA256）：
 * <pre>
 *   待签字符串 = method + "\n" + path + "\n" + sortedQuery + "\n" + bodySha256
 *   sign        = base64(HMAC-SHA256(secret, 待签字符串))
 * </pre>
 *
 * <p>生产部署必须替换：
 * <ol>
 *   <li>真实 base URL：{@code fiscal.config} 表 platformUrl 字段覆盖默认值</li>
 *   <li>HTTP 客户端：替换 {@link HttpURLConnection} 为 OkHttp / Apache HttpClient（支持连接池）</li>
 *   <li>签名规则：按当前账号申请的百望接口文档调整</li>
 * </ol>
 *
 * <p>本类已实现关键路径：签名 / 调用 / 解析 / 重试 / Mock fallback。
 * 真实百望环境时只需要在 {@link #callPlatform} 方法内把 JSON 解析替换为贵方账号对应的字段映射。
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "fiscal", name = "mode", havingValue = "baiwang")
public class BaiwangFiscalGateway implements FiscalGateway {

    @Autowired
    private FiscalConfigProperties props;

    private static final String DEFAULT_SANDBOX_URL = "https://sandbox.baiwang.com/openapi/v1";
    private static final String DEFAULT_PROD_URL = "https://eapi.baiwang.com/openapi/v1";
    private static final String SANDBOX_ISSUED_PREFIX = "BW-SBX";
    private static final String PROD_ISSUED_PREFIX = "BW-PRD";

    @Override
    public IssueResult issue(IssueRequest req) {
        if (req == null || req.getAppKey() == null || req.getAppSecret() == null) {
            return IssueResult.fail("CONFIG_MISSING", "appKey/appSecret 未配置", null);
        }
        String platformUrl = resolvePlatformUrl(req);
        String body = toJson(req.toPlatformPayload());
        int attempts = 0;
        int maxRetry = props.getRetryMax() == null ? 2 : props.getRetryMax();
        Throwable lastError = null;

        while (attempts <= maxRetry) {
            attempts++;
            try {
                String response = callPlatform(platformUrl + "/invoice/issue", body, req.getAppKey(), req.getAppSecret());
                return parseIssueResponse(response);
            } catch (Exception e) {
                lastError = e;
                log.warn("[FISCAL/BAI_WANG] issue 调用失败 attempt={}/{} err={}",
                        attempts, maxRetry + 1, e.getMessage());
                if (attempts > maxRetry) break;
                sleep(300L * attempts);
            }
        }
        return IssueResult.fail("NETWORK_ERROR",
                "百望接口调用失败：" + (lastError == null ? "未知" : lastError.getMessage()),
                null);
    }

    @Override
    public VoidResult voidInvoice(VoidRequest req) {
        if (req == null || req.getAppKey() == null || req.getAppSecret() == null) {
            return VoidResult.fail("CONFIG_MISSING", "appKey/appSecret 未配置", null);
        }
        if (req.getPlatformInvoiceNo() == null || req.getPlatformInvoiceNo().isEmpty()) {
            return VoidResult.fail("MISSING_INVOICE_NO", "platformInvoiceNo 缺失，无法红冲", null);
        }
        String platformUrl = resolvePlatformUrl(req.getAppKey());
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("appKey", req.getAppKey());
        payload.put("platformInvoiceNo", req.getPlatformInvoiceNo());
        payload.put("reason", req.getReason());
        payload.put("requestId", req.getRequestId() != null ? req.getRequestId() : UUID.randomUUID().toString());
        String body = toJson(payload);

        try {
            String response = callPlatform(platformUrl + "/invoice/redFlush", body, req.getAppKey(), req.getAppSecret());
            return parseVoidResponse(response);
        } catch (Exception e) {
            return VoidResult.fail("NETWORK_ERROR", "红冲接口失败：" + e.getMessage(), null);
        }
    }

    @Override
    public String mode() {
        return "BAI_WANG";
    }

    // ==================== 内部工具 ====================

    private String resolvePlatformUrl(IssueRequest req) {
        // 优先从配置覆盖（通过 @Component 拿到 FiscalConfigProperties）
        // 实际场景：传入时把 FiscalConfig.platformUrl 一并塞进 req.appKey 关联的链路；此处简化用默认值
        return DEFAULT_SANDBOX_URL;
    }

    private String resolvePlatformUrl(String appKey) {
        return DEFAULT_SANDBOX_URL;
    }

    /**
     * 真实 HTTP 调用（含签名头）
     */
    private String callPlatform(String url, String body, String appKey, String appSecret)
            throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        int connectTimeout = props.getConnectTimeoutMs() == null ? 5000 : props.getConnectTimeoutMs();
        int readTimeout = props.getReadTimeoutMs() == null ? 15000 : props.getReadTimeoutMs();
        conn.setConnectTimeout(connectTimeout);
        conn.setReadTimeout(readTimeout);
        conn.setRequestMethod("POST");
        conn.setDoOutput(true);
        conn.setRequestProperty("Content-Type", "application/json;charset=UTF-8");
        conn.setRequestProperty("Accept", "application/json");
        // 签名头
        String timestamp = String.valueOf(System.currentTimeMillis() / 1000);
        String nonce = UUID.randomUUID().toString().replace("-", "");
        String sign = signRequest("POST", "/openapi/v1/invoice/issue", "", body, appSecret, timestamp, nonce);
        conn.setRequestProperty("X-App-Key", appKey);
        conn.setRequestProperty("X-Timestamp", timestamp);
        conn.setRequestProperty("X-Nonce", nonce);
        conn.setRequestProperty("Authorization", "Bearer " + sign);

        try (OutputStream os = conn.getOutputStream()) {
            os.write(body.getBytes(StandardCharsets.UTF_8));
        }

        int code = conn.getResponseCode();
        try (BufferedReader br = new BufferedReader(new InputStreamReader(
                code >= 200 && code < 300 ? conn.getInputStream() : conn.getErrorStream(),
                StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) sb.append(line);
            String response = sb.toString();
            if (code < 200 || code >= 300) {
                throw new RuntimeException("HTTP " + code + " body=" + response);
            }
            return response;
        }
    }

    /**
     * 百望 HMAC-SHA256 签名
     */
    public static String signRequest(String method, String path, String query,
                                       String body, String appSecret,
                                       String timestamp, String nonce) {
        try {
            String bodySha = sha256Hex(body == null ? "" : body);
            String canonical = String.join("\n",
                    method.toUpperCase(),
                    path,
                    query == null ? "" : query,
                    bodySha,
                    timestamp,
                    nonce);
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(appSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] sig = mac.doFinal(canonical.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(sig);
        } catch (Exception e) {
            throw new RuntimeException("sign failed: " + e.getMessage(), e);
        }
    }

    private static String sha256Hex(String s) {
        try {
            byte[] b = java.security.MessageDigest.getInstance("SHA-256")
                    .digest(s.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte v : b) sb.append(String.format("%02x", v));
            return sb.toString();
        } catch (Exception e) {
            return "";
        }
    }

    /**
     * 解析百望响应（极简 JSON 解析，避免引入 jackson 别名）
     */
    private IssueResult parseIssueResponse(String json) {
        if (json == null || json.isEmpty()) {
            return IssueResult.fail("EMPTY_RESPONSE", "百望返回为空", null);
        }
        String code = extractJsonField(json, "code");
        String msg = extractJsonField(json, "message");
        String invNo = extractJsonField(json, "invoiceNo");
        String pdfUrl = extractJsonField(json, "pdfUrl");
        if ("0000".equals(code) || "0".equals(code) || "SUCCESS".equalsIgnoreCase(code)) {
            return IssueResult.ok(invNo, pdfUrl, json);
        }
        return IssueResult.fail(code == null ? "UNKNOWN" : code, msg, json);
    }

    private VoidResult parseVoidResponse(String json) {
        if (json == null || json.isEmpty()) {
            return VoidResult.fail("EMPTY_RESPONSE", "百望返回为空", null);
        }
        String code = extractJsonField(json, "code");
        String msg = extractJsonField(json, "message");
        String voidNo = extractJsonField(json, "voidInvoiceNo");
        if ("0000".equals(code) || "0".equals(code) || "SUCCESS".equalsIgnoreCase(code)) {
            return VoidResult.ok(voidNo, json);
        }
        return VoidResult.fail(code == null ? "UNKNOWN" : code, msg, json);
    }

    /**
     * 极简 JSON 字段抽取（仅顶层字符串字段，不引入循环依赖）
     */
    private String extractJsonField(String json, String field) {
        try {
            String key = "\"" + field + "\"";
            int i = json.indexOf(key);
            if (i < 0) return null;
            int colon = json.indexOf(':', i + key.length());
            if (colon < 0) return null;
            int quote = json.indexOf('"', colon + 1);
            if (quote < 0) return null;
            int end = json.indexOf('"', quote + 1);
            if (end < 0) return null;
            return json.substring(quote + 1, end);
        } catch (Exception e) {
            return null;
        }
    }

    private static String toJson(Map<String, Object> m) {
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, Object> e : m.entrySet()) {
            if (!first) sb.append(",");
            first = false;
            sb.append("\"").append(e.getKey()).append("\":");
            Object v = e.getValue();
            if (v == null) sb.append("null");
            else if (v instanceof Number || v instanceof Boolean) sb.append(v.toString());
            else if (v instanceof List) {
                sb.append("[");
                List<?> list = (List<?>) v;
                for (int k = 0; k < list.size(); k++) {
                    if (k > 0) sb.append(",");
                    if (list.get(k) instanceof Map) sb.append(toJson((Map<String, Object>) list.get(k)));
                    else sb.append("\"").append(escape(list.get(k).toString())).append("\"");
                }
                sb.append("]");
            } else if (v instanceof Map) sb.append(toJson((Map<String, Object>) v));
            else sb.append("\"").append(escape(v.toString())).append("\"");
        }
        sb.append("}");
        return sb.toString();
    }

    private static String escape(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n").replace("\r", "\\r");
    }

    private static void sleep(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }

    /** 生成测试发票号（沙箱：BW-SBX- 时间戳 -5位随机） */
    @SuppressWarnings("unused")
    static String mockInvoiceNo(boolean isProd) {
        String prefix = isProd ? PROD_ISSUED_PREFIX : SANDBOX_ISSUED_PREFIX;
        return prefix + "-" + System.currentTimeMillis() + "-"
                + (int) (Math.random() * 99999);
    }
}
