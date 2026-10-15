package com.meiyetec.ecommerce;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.meiyetec.entity.EcommercePlatform;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.*;

/**
 * EXT-16：有赞（Youzan）电商适配器（kdtapi v1.0）
 *
 * <p>鉴权（OAuth2 Client-Credentials）：
 * <pre>
 *   POST https://openapi.youzan.com/api/oauthentry/grant/token
 *   body: client_id={appKey}&client_secret={appSecret}&grant_type=client_credential
 *   resp: {"access_token":"xxx","expires_in":7200,"token_type":"Bearer","scope":"all"}
 * </pre>
 *
 * <p>商品拉取：
 * <pre>
 *   GET https://openapi.youzan.com/api/entry/kdtapi/v1.0/items/list
 *       ?page_no=1&amp;page_size=50&amp;access_token={token}
 *   resp: {"data":{"items":[...]},"total_results":N}
 * </pre>
 *
 * <p>订单拉取：
 * <pre>
 *   GET https://openapi.youzan.com/api/entry/kdtapi/v1.0/trades/sold_get
 *       ?start_update_time=2026-09-01 00:00:00&amp;end_update_time=2026-09-05 00:00:00
 *       &amp;page_no=1&amp;page_size=50&amp;access_token={token}
 *   resp: {"data":{"trades":[...]},"total_results":N}
 * </pre>
 *
 * <p>Token 缓存：基于 {@code ecommerce_platform.token_expire_at}（提前 2 分钟过期）。
 */
@Slf4j
@Component
public class YouzanEcommerceAdapter implements EcommerceAdapter {

    /** 有赞开放平台默认入口 */
    private static final String DEFAULT_API_BASE = "https://openapi.youzan.com";
    /** OAuth2 token 端点 */
    private static final String TOKEN_ENDPOINT  = "/api/oauthentry/grant/token";
    /** kdtapi 入口前缀 */
    private static final String ENTRY_PREFIX    = "/api/entry/kdtapi/v1.0";

    @Autowired(required = false)
    private ObjectMapper jackson;

    @Override
    public String platformCode() {
        return "YOUZAN";
    }

    // ============================================================
    //  URL 构造（暴露为 package-private，便于单测验证）
    // ============================================================

    /** 构造 OAuth2 token 请求 body（application/x-www-form-urlencoded） */
    static String buildTokenRequestBody(String clientId, String clientSecret) {
        return "client_id=" + java.net.URLEncoder.encode(clientId, java.nio.charset.StandardCharsets.UTF_8)
                + "&client_secret=" + java.net.URLEncoder.encode(clientSecret, java.nio.charset.StandardCharsets.UTF_8)
                + "&grant_type=client_credential";
    }

    /** 构造 kdtapi items/list URL（含 access_token） */
    static String buildItemsListUrl(String base, String token, int pageNo, int pageSize) {
        return base + ENTRY_PREFIX + "/items/list"
                + "?page_no=" + pageNo
                + "&page_size=" + Math.min(pageSize, 100)
                + "&access_token=" + java.net.URLEncoder.encode(token, java.nio.charset.StandardCharsets.UTF_8);
    }

    /** 构造 kdtapi trades/sold_get URL（含 access_token + 时间窗） */
    static String buildTradesSoldGetUrl(String base, String token, String startTime, String endTime, int pageNo, int pageSize) {
        StringBuilder url = new StringBuilder(base).append(ENTRY_PREFIX).append("/trades/sold_get")
                .append("?page_no=").append(pageNo)
                .append("&page_size=").append(Math.min(pageSize, 100))
                .append("&access_token=").append(java.net.URLEncoder.encode(token, java.nio.charset.StandardCharsets.UTF_8));
        if (startTime != null) url.append("&start_update_time=").append(java.net.URLEncoder.encode(startTime, java.nio.charset.StandardCharsets.UTF_8));
        if (endTime != null)   url.append("&end_update_time=").append(java.net.URLEncoder.encode(endTime, java.nio.charset.StandardCharsets.UTF_8));
        return url.toString();
    }

    /**
     * 构造 webhook 回调推送地址（基于 kdtId）。
     * <p>有赞 push_url 模板：{@code https://{your-domain}/webhook/youzan?kdt_id={kdtId}}
     */
    static String buildWebhookUrl(String baseUrl, String kdtId) {
        if (baseUrl == null || baseUrl.isEmpty()) return null;
        StringBuilder sb = new StringBuilder(baseUrl);
        if (!baseUrl.contains("?")) sb.append('?');
        else sb.append('&');
        sb.append("kdt_id=").append(java.net.URLEncoder.encode(kdtId == null ? "" : kdtId, java.nio.charset.StandardCharsets.UTF_8));
        return sb.toString();
    }

    /**
     * 验证有赞 webhook 签名（{@code X-Youzan-Sign} 头）。
     * <p>算法：{@code md5(signSecret + full_body)}，全部小写。
     * <p>供 {@code YouzanWebhookController} 调用；保持 public 以跨包访问。
     */
    public static boolean verifyWebhookSignature(String signSecret, String rawBody, String signature) {
        if (signSecret == null || signSecret.isEmpty()) return false;
        if (signature == null || signature.isEmpty())   return false;
        if (rawBody == null) rawBody = "";
        try {
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("MD5");
            byte[] digest = md.digest((signSecret + rawBody).getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(digest.length * 2);
            for (byte b : digest) hex.append(String.format("%02x", b));
            String expected = hex.toString();
            boolean ok = expected.equalsIgnoreCase(signature);
            if (!ok) log.warn("[ECOMMERCE/YOUZAN] webhook signature mismatch: expected={} got={}", expected, signature);
            return ok;
        } catch (Exception e) {
            log.warn("[ECOMMERCE/YOUZAN] webhook signature verify failed: {}", e.getMessage());
            return false;
        }
    }

    // ============================================================
    //  鉴权
    // ============================================================

    @Override
    public AuthResult authenticate(EcommercePlatform cfg) {
        long start = System.currentTimeMillis();
        if (cfg == null) return AuthResult.fail("CONFIG_MISSING", "配置缺失");
        if (cfg.getAppKey() == null || cfg.getAppSecret() == null) {
            return AuthResult.fail("OAUTH2_INPUT_MISSING", "appKey / appSecret 必填");
        }

        // 1. 缓存命中：accessToken 非空 + 距离过期 > 2 分钟
        if (cfg.getAccessToken() != null && cfg.getTokenExpireAt() != null
                && cfg.getTokenExpireAt().isAfter(LocalDateTime.now().plusMinutes(2))) {
            AuthResult r = new AuthResult();
            r.success = true;
            r.accessToken = cfg.getAccessToken();
            long secs = (cfg.getTokenExpireAt().atZone(java.time.ZoneId.systemDefault())
                    .toInstant().toEpochMilli() - System.currentTimeMillis()) / 1000;
            r.expiresIn = secs;
            return r;
        }

        // 2. 重新签发
        try {
            String base = cfg.getApiBaseUrl() == null ? DEFAULT_API_BASE : cfg.getApiBaseUrl();
            String url = base + TOKEN_ENDPOINT;
            String body = "client_id=" + URLEncoder.encode(cfg.getAppKey(), "UTF-8")
                    + "&client_secret=" + URLEncoder.encode(cfg.getAppSecret(), "UTF-8")
                    + "&grant_type=client_credential";
            String response = post(url, body, "application/x-www-form-urlencoded",
                    null, 5000, 15000);
            JsonNode node = jackson.readTree(response);
            String token = node.path("access_token").asText(null);
            long expiresIn = node.path("expires_in").asLong(7200);
            if (token == null || token.isEmpty()) {
                return AuthResult.fail("OAUTH2_NO_TOKEN", "响应未包含 access_token: " + response);
            }
            // 写回缓存
            cfg.setAccessToken(token);
            cfg.setTokenExpireAt(LocalDateTime.now().plusSeconds(expiresIn));
            log.info("[ECOMMERCE/YOUZAN] authenticated: shop={} expiresIn={}s elapsed={}ms",
                    cfg.getShopId(), expiresIn, System.currentTimeMillis() - start);
            return AuthResult.ok(token, expiresIn);
        } catch (Exception e) {
            log.warn("[ECOMMERCE/YOUZAN] authenticate failed: {}", e.getMessage());
            return AuthResult.fail("OAUTH2_FAILED", e.getMessage());
        }
    }

    // ============================================================
    //  商品拉取
    // ============================================================

    @Override
    public ProductPage fetchProducts(EcommercePlatform cfg, int pageNo, int pageSize) {
        AuthResult auth = authenticate(cfg);
        ProductPage page = new ProductPage();
        page.items = List.of();
        if (!auth.success) {
            log.warn("[ECOMMERCE/YOUZAN] fetchProducts skip: auth failed: {}", auth.errorMessage);
            return page;
        }
        try {
            String base = cfg.getApiBaseUrl() == null ? DEFAULT_API_BASE : cfg.getApiBaseUrl();
            String url = base + ENTRY_PREFIX + "/items/list"
                    + "?page_no=" + pageNo
                    + "&page_size=" + Math.min(pageSize, 100)
                    + "&access_token=" + URLEncoder.encode(auth.accessToken, "UTF-8");
            String response = get(url);
            JsonNode root = jackson.readTree(response);
            JsonNode data = root.path("data");
            JsonNode items = data.path("items");
            List<Map<String, Object>> out = new ArrayList<>();
            if (items.isArray()) {
                for (JsonNode n : items) out.add(jackson.convertValue(n, Map.class));
            }
            page.items = out;
            page.totalCount = root.path("total_results").asInt(items.size());
            page.hasNext = items.size() >= pageSize;
            log.info("[ECOMMERCE/YOUZAN] fetchProducts: page={} size={} returned={} total={}",
                    pageNo, pageSize, out.size(), page.totalCount);
            return page;
        } catch (Exception e) {
            log.warn("[ECOMMERCE/YOUZAN] fetchProducts failed: {}", e.getMessage());
            return page;
        }
    }

    // ============================================================
    //  订单拉取
    // ============================================================

    @Override
    public OrderPage fetchOrders(EcommercePlatform cfg,
                                 String startTime, String endTime,
                                 int pageNo, int pageSize) {
        AuthResult auth = authenticate(cfg);
        OrderPage page = new OrderPage();
        page.orders = List.of();
        if (!auth.success) {
            log.warn("[ECOMMERCE/YOUZAN] fetchOrders skip: auth failed: {}", auth.errorMessage);
            return page;
        }
        try {
            String base = cfg.getApiBaseUrl() == null ? DEFAULT_API_BASE : cfg.getApiBaseUrl();
            StringBuilder url = new StringBuilder(base).append(ENTRY_PREFIX).append("/trades/sold_get")
                    .append("?page_no=").append(pageNo)
                    .append("&page_size=").append(Math.min(pageSize, 100))
                    .append("&access_token=").append(URLEncoder.encode(auth.accessToken, "UTF-8"));
            if (startTime != null) url.append("&start_update_time=").append(URLEncoder.encode(startTime, "UTF-8"));
            if (endTime != null)   url.append("&end_update_time=").append(URLEncoder.encode(endTime, "UTF-8"));
            String response = get(url.toString());
            JsonNode root = jackson.readTree(response);
            JsonNode data = root.path("data");
            JsonNode trades = data.path("trades");
            List<Map<String, Object>> out = new ArrayList<>();
            if (trades.isArray()) {
                for (JsonNode n : trades) out.add(jackson.convertValue(n, Map.class));
            }
            page.orders = out;
            page.totalCount = root.path("total_results").asInt(trades.size());
            page.hasNext = trades.size() >= pageSize;
            log.info("[ECOMMERCE/YOUZAN] fetchOrders: page={} size={} returned={} total={} window=[{}..{}]",
                    pageNo, pageSize, out.size(), page.totalCount, startTime, endTime);
            return page;
        } catch (Exception e) {
            log.warn("[ECOMMERCE/YOUZAN] fetchOrders failed: {}", e.getMessage());
            return page;
        }
    }

    // ============================================================
    //  健康检查
    // ============================================================

    @Override
    public HealthStatus healthCheck(EcommercePlatform cfg) {
        long start = System.currentTimeMillis();
        HealthStatus s = new HealthStatus();
        AuthResult auth = authenticate(cfg);
        if (!auth.success) {
            s.healthy = false;
            s.stage = "AUTH_FAIL";
            s.message = auth.errorMessage;
            s.elapsedMs = System.currentTimeMillis() - start;
            return s;
        }
        s.healthy = true;
        s.stage = "AUTH_OK";
        s.message = "Youzan OAuth2 鉴权通过";
        s.apiBaseUrl = cfg.getApiBaseUrl() == null ? DEFAULT_API_BASE : cfg.getApiBaseUrl();
        s.elapsedMs = System.currentTimeMillis() - start;
        return s;
    }

    // ============================================================
    //  HTTP 工具
    // ============================================================

    private String post(String url, String body, String contentType,
                         String bearer, int connectTimeout, int readTimeout) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        conn.setRequestMethod("POST");
        conn.setConnectTimeout(connectTimeout);
        conn.setReadTimeout(readTimeout);
        conn.setDoOutput(true);
        conn.setRequestProperty("Content-Type", contentType);
        conn.setRequestProperty("Accept", "application/json");
        if (bearer != null) conn.setRequestProperty("Authorization", "Bearer " + bearer);
        try (OutputStream os = conn.getOutputStream()) {
            os.write(body.getBytes(StandardCharsets.UTF_8));
        }
        return readResponse(conn);
    }

    private String get(String url) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        conn.setRequestMethod("GET");
        conn.setConnectTimeout(5000);
        conn.setReadTimeout(15000);
        conn.setRequestProperty("Accept", "application/json");
        return readResponse(conn);
    }

    private String readResponse(HttpURLConnection conn) throws Exception {
        int code = conn.getResponseCode();
        try (BufferedReader br = new BufferedReader(new InputStreamReader(
                code >= 200 && code < 300 ? conn.getInputStream() : conn.getErrorStream(),
                StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) sb.append(line);
            String body = sb.toString();
            if (code < 200 || code >= 300) {
                throw new RuntimeException("HTTP " + code + ": " + body);
            }
            return body;
        }
    }
}
