package com.meiyetec.crm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.meiyetec.entity.CrmIntegration;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;

/**
 * EXT-15 + EXT-24：Zoho CRM 适配器
 *
 * <p>鉴权：OAuth2 Authorization Code / Refresh Token
 * <pre>
 *   POST https://accounts.zoho.com/oauth/v2/token
 *   grant_type=refresh_token
 *   &client_id=...&client_secret=...&refresh_token=...
 *   → { access_token, refresh_token, expires_in(秒) }
 *   默认 domain：accounts.zoho.com / www.zohoapis.com
 *   国内/欧：accounts.zoho.com.cn / www.zohoapis.com.cn
 *   欧：accounts.zoho.eu
 * </pre>
 *
 * <p>Contact 增量同步（COQL 查询）：
 * <pre>
 *   POST https://www.zohoapis.com/crm/v6/contacts/search
 *   {
 *     "select_query": "select First_Name,Last_Name,Email,Phone,Title,Modified_Time
 *                      from Contacts where Modified_Time > '2026-09-01T00:00:00+08:00'
 *                      order by Modified_Time asc limit 200"
 *   }
 * </pre>
 *
 * <p>upsert（按 Email 幂等）：
 * <pre>
 *   POST https://www.zohoapis.com/crm/v6/Contacts/upsert
 *   {
 *     "data": [{ "Email": "x@y.com", "First_Name": "...", ... }],
 *     "duplicate_check_fields": ["Email"],
 *     "apply_feature_execution_rules": true
 *   }
 * </pre>
 *
 * <p>字段映射（DEFAULT_FIELD_MAP）：
 * <pre>
 *   externalId  → id                   (Contact 主键 UUID)
 *   firstName   → First_Name
 *   lastName    → Last_Name
 *   email       → Email
 *   phone       → Phone
 *   title       → Title
 *   updatedAtMs → Modified_Time        (ISO 8601: 2026-09-01T00:00:00+08:00)
 * </pre>
 */
@Slf4j
@Component
public class ZohoCrmAdapter implements CrmAdapter {

    @Autowired(required = false)
    private ObjectMapper jackson;

    @Override
    public String platformCode() {
        return "ZOHO";
    }

    // ============================================================
    //  默认字段映射
    // ============================================================
    static final Map<String, String> DEFAULT_FIELD_MAP = new LinkedHashMap<>();
    static {
        DEFAULT_FIELD_MAP.put("externalId", "id");
        DEFAULT_FIELD_MAP.put("firstName",  "First_Name");
        DEFAULT_FIELD_MAP.put("lastName",   "Last_Name");
        DEFAULT_FIELD_MAP.put("email",      "Email");
        DEFAULT_FIELD_MAP.put("phone",      "Phone");
        DEFAULT_FIELD_MAP.put("title",      "Title");
        DEFAULT_FIELD_MAP.put("updatedAtMs","Modified_Time");
    }

    /** 解析 {@code CrmIntegration.objectMappings} JSON，merge 到默认映射 */
    public static Map<String, String> parseObjectMappings(CrmIntegration cfg) {
        Map<String, String> fallback = new LinkedHashMap<>(DEFAULT_FIELD_MAP);
        if (cfg == null || cfg.getObjectMappings() == null || cfg.getObjectMappings().isEmpty()) {
            return fallback;
        }
        try {
            JsonNode node = new ObjectMapper().readTree(cfg.getObjectMappings());
            if (node.isObject()) {
                Map<String, String> out = new LinkedHashMap<>();
                node.fields().forEachRemaining(e -> {
                    String k = e.getKey();
                    String v = e.getValue().asText();
                    if (k != null && v != null && !v.isEmpty()) out.put(k, v);
                });
                if (!out.isEmpty()) {
                    for (Map.Entry<String, String> e : fallback.entrySet()) {
                        out.putIfAbsent(e.getKey(), e.getValue());
                    }
                    return out;
                }
            }
        } catch (Exception e) {
            log.warn("[CRM/ZOHO] objectMappings 解析失败，使用默认：{}", e.getMessage());
        }
        return fallback;
    }

    // ============================================================
    //  鉴权（OAuth2 refresh_token 流）
    // ============================================================
    @Override
    public AuthResult authenticate(CrmIntegration cfg) {
        if (cfg == null) return AuthResult.fail("CONFIG_MISSING", "配置缺失");
        String apiBase = cfg.getApiBaseUrl() == null
                ? "https://www.zohoapis.com"
                : cfg.getApiBaseUrl();
        String accountsBase = deriveAccountsBase(apiBase);

        // 1) 缓存命中
        if (cfg.getAccessToken() != null && cfg.getTokenExpireAt() != null
                && cfg.getTokenExpireAt().isAfter(java.time.LocalDateTime.now().plusMinutes(2))) {
            AuthResult r = new AuthResult();
            r.success = true;
            r.accessToken = cfg.getAccessToken();
            r.instanceUrl = apiBase;
            r.expiresIn = (cfg.getTokenExpireAt().atZone(java.time.ZoneId.systemDefault())
                    .toInstant().toEpochMilli() - System.currentTimeMillis()) / 1000;
            return r;
        }

        // 2) refresh_token 换 access_token
        if (cfg.getClientId() == null || cfg.getClientSecret() == null || cfg.getRefreshToken() == null) {
            return AuthResult.fail("ZOHO_OAUTH_INPUT_MISSING",
                    "clientId / clientSecret / refreshToken 必填");
        }
        try {
            String tokenUrl = accountsBase + "/oauth/v2/token";
            String body = "grant_type=refresh_token"
                    + "&client_id=" + urlEncode(cfg.getClientId())
                    + "&client_secret=" + urlEncode(cfg.getClientSecret())
                    + "&refresh_token=" + urlEncode(cfg.getRefreshToken());
            String response = post(tokenUrl, body, "application/x-www-form-urlencoded",
                    null, 5000, 15000);
            JsonNode node = jackson.readTree(response);
            if (node.has("error")) {
                return AuthResult.fail(node.path("error").asText("ZOHO_AUTH_ERROR"),
                        node.path("error_description").asText(response));
            }
            AuthResult r = new AuthResult();
            r.success = true;
            r.accessToken = node.path("access_token").asText();
            r.refreshToken = node.path("refresh_token").asText(cfg.getRefreshToken());
            r.expiresIn = node.path("expires_in").asLong(3600);
            r.instanceUrl = apiBase;
            log.info("[CRM/ZOHO] OAuth2 refresh OK: expiresIn={}s base={}", r.expiresIn, apiBase);
            return r;
        } catch (Exception e) {
            log.warn("[CRM/ZOHO] authenticate failed: {}", e.getMessage());
            return AuthResult.fail("ZOHO_AUTH_FAILED", e.getMessage());
        }
    }

    /**
     * 由 {@code apiBaseUrl} 推导账号域名（accounts.zoho.com / .com.cn / .eu）。
     * <p>默认 {@code https://www.zohoapis.com} → {@code https://accounts.zoho.com}
     */
    static String deriveAccountsBase(String apiBase) {
        if (apiBase == null) return "https://accounts.zoho.com";
        String a = apiBase.toLowerCase();
        if (a.contains("zohoapis.com.cn") || a.contains("zoho.com.cn")) return "https://accounts.zoho.com.cn";
        if (a.contains("zohoapis.eu"))  return "https://accounts.zoho.eu";
        if (a.contains("zohoapis.in"))  return "https://accounts.zoho.in";
        if (a.contains("zohoapis.com.au")) return "https://accounts.zoho.com.au";
        return "https://accounts.zoho.com";
    }

    // ============================================================
    //  增量拉取 Contact（COQL）
    // ============================================================
    @Override
    public List<ExternalContact> fetchContactsIncremental(CrmIntegration cfg, SyncCursor cursor) {
        AuthResult auth = authenticate(cfg);
        if (!auth.success) {
            log.warn("[CRM/ZOHO] fetchContacts skip: auth failed: {}", auth.errorMessage);
            return List.of();
        }

        Map<String, String> fieldMap = parseObjectMappings(cfg);
        // 1) 构造 COQL：select [字段] from Contacts where Modified_Time > 'ISO' order by Modified_Time asc limit 200
        String modField = fieldMap.getOrDefault("updatedAtMs", "Modified_Time");
        String sinceIso = cursor == null || cursor.sinceEpochMs == null
                ? "1970-01-01T00:00:00+00:00"
                : Instant.ofEpochMilli(cursor.sinceEpochMs).toString();

        List<String> selects = new ArrayList<>();
        selects.add(fieldMap.getOrDefault("externalId", "id"));
        selects.add(modField);
        for (Map.Entry<String, String> e : fieldMap.entrySet()) {
            if ("externalId".equals(e.getKey()) || "updatedAtMs".equals(e.getKey())) continue;
            if (e.getValue() != null && !e.getValue().isEmpty()) selects.add(e.getValue());
        }

        StringBuilder soql = new StringBuilder();
        soql.append("select ").append(String.join(",", selects))
                .append(" from Contacts")
                .append(" where ").append(modField).append(" > '").append(sinceIso).append("'")
                .append(" order by ").append(modField).append(" asc")
                .append(" limit 200");

        String url = auth.instanceUrl + "/crm/v6/contacts/search";
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("select_query", soql.toString());
        if (cursor != null && cursor.knownExternalIds != null && !cursor.knownExternalIds.isEmpty()) {
            log.info("[CRM/ZOHO] knownIds={} (差集在 upsert 阶段去重)", cursor.knownExternalIds.size());
        }

        try {
            String response = post(url, jackson.writeValueAsString(body),
                    "application/json", auth.accessToken, 5000, 30000);
            JsonNode root = jackson.readTree(response);
            JsonNode records = root.path("data");
            List<ExternalContact> out = new ArrayList<>();
            if (records.isArray()) {
                String extIdKey = fieldMap.getOrDefault("externalId", "id");
                String firstKey = fieldMap.getOrDefault("firstName", "First_Name");
                String lastKey  = fieldMap.getOrDefault("lastName",  "Last_Name");
                String emailKey = fieldMap.getOrDefault("email",     "Email");
                String phoneKey = fieldMap.getOrDefault("phone",     "Phone");
                String titleKey = fieldMap.getOrDefault("title",     "Title");
                String modKey   = fieldMap.getOrDefault("updatedAtMs","Modified_Time");

                for (JsonNode r : records) {
                    ExternalContact c = new ExternalContact();
                    c.externalId = textOrNull(r.path(extIdKey));
                    c.firstName  = textOrNull(r.path(firstKey));
                    c.lastName   = textOrNull(r.path(lastKey));
                    c.email      = textOrNull(r.path(emailKey));
                    c.phone      = textOrNull(r.path(phoneKey));
                    c.title      = textOrNull(r.path(titleKey));
                    String mod = textOrNull(r.path(modKey));
                    if (mod != null) {
                        try {
                            // Zoho Modified_Time: '2026-09-01T00:00:00+08:00'
                            c.updatedAtMs = Instant.parse(mod).toEpochMilli();
                        } catch (Exception ignored) {}
                    }
                    if (c.externalId != null) out.add(c);
                }
            }
            log.info("[CRM/ZOHO] fetchContacts: {} 条 (mapping fields={})", out.size(), fieldMap.values());
            return out;
        } catch (Exception e) {
            log.warn("[CRM/ZOHO] fetchContacts failed: {}", e.getMessage());
            return List.of();
        }
    }

    // ============================================================
    //  Upsert Contact（POST /Contacts/upsert by Email）
    // ============================================================
    @Override
    public PushResult upsertContact(CrmIntegration cfg, Map<String, Object> contact) {
        AuthResult auth = authenticate(cfg);
        if (!auth.success) return PushResult.fail(auth.errorCode, auth.errorMessage);

        Map<String, String> fieldMap = parseObjectMappings(cfg);
        String url = auth.instanceUrl + "/crm/v6/Contacts/upsert";

        Map<String, Object> data = new LinkedHashMap<>();
        putIfPresent(data, fieldMap.get("firstName"), contact.get("firstName"));
        putIfPresent(data, fieldMap.get("lastName"),  contact.get("lastName"));
        putIfPresent(data, fieldMap.get("email"),     contact.get("email"));
        putIfPresent(data, fieldMap.get("phone"),     contact.get("phone"));
        putIfPresent(data, fieldMap.get("title"),     contact.get("title"));
        Object custom = contact.get("customFields");
        if (custom instanceof Map) {
            ((Map<?, ?>) custom).forEach((k, v) -> data.put(String.valueOf(k), v));
        }
        if (data.isEmpty()) return PushResult.fail("EMPTY_PAYLOAD", "无可写入字段");

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("data", List.of(data));
        body.put("duplicate_check_fields", List.of("Email"));
        body.put("apply_feature_execution_rules", true);

        try {
            String response = post(url, jackson.writeValueAsString(body),
                    "application/json", auth.accessToken, 5000, 15000);
            JsonNode root = jackson.readTree(response);
            JsonNode arr = root.path("data");
            if (arr.isArray() && arr.size() > 0) {
                JsonNode first = arr.get(0);
                JsonNode details = first.path("details");
                String id = details.path("id").asText(null);
                if (id == null) id = textOrNull(first.path("id"));
                String etag = details.path("Modified_Time").asText("v1");
                return PushResult.ok(id, etag);
            }
            // Zoho 失败时响应里有 code/message
            String code = root.path("code").asText("ZOHO_EMPTY_RESPONSE");
            String msg = root.path("message").asText(response);
            return PushResult.fail(code, msg);
        } catch (Exception e) {
            return PushResult.fail("ZOHO_UPSERT_FAILED", e.getMessage());
        }
    }

    @Override
    public HealthStatus healthCheck(CrmIntegration cfg) {
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
        // 轻量探活：访问 GET /crm/v6/Contacts?per_page=1
        try {
            String url = auth.instanceUrl + "/crm/v6/Contacts?per_page=1&fields=id";
            get(url, auth.accessToken);
            s.healthy = true;
            s.stage = "AUTH_AND_QUERY_OK";
            s.message = "Zoho 鉴权 + 查询通过";
            s.apiBaseUrl = auth.instanceUrl;
        } catch (Exception e) {
            s.healthy = false;
            s.stage = "QUERY_FAIL";
            s.message = e.getMessage();
            s.apiBaseUrl = auth.instanceUrl;
        }
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

    private String get(String url, String bearer) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        conn.setRequestMethod("GET");
        conn.setConnectTimeout(5000);
        conn.setReadTimeout(15000);
        conn.setRequestProperty("Accept", "application/json");
        if (bearer != null) conn.setRequestProperty("Authorization", "Bearer " + bearer);
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

    private static String urlEncode(String s) {
        if (s == null) return "";
        try { return java.net.URLEncoder.encode(s, "UTF-8"); }
        catch (Exception e) { return s; }
    }

    private static String textOrNull(JsonNode n) {
        if (n == null || n.isMissingNode() || n.isNull()) return null;
        String s = n.asText();
        return (s == null || s.isEmpty() || "null".equals(s)) ? null : s;
    }

    private static void putIfPresent(Map<String, Object> map, String key, Object value) {
        if (key == null || key.isEmpty() || value == null) return;
        map.put(key, value);
    }
}
