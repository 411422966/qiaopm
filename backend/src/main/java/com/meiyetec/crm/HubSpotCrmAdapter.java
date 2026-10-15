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
 * EXT-15 + EXT-24：HubSpot CRM 适配器
 *
 * <p>鉴权：Private App access token (PAT) / OAuth2 access token (Bearer)
 * <pre>
 *   - PAT 模式：直接把 token 放进 Authorization: Bearer header
 *   - OAuth2 模式：clientId/clientSecret → POST /oauth/v1/token
 * </pre>
 *
 * <p>Contact 增量同步（search API）：
 * <pre>
 *   POST https://api.hubapi.com/crm/v3/objects/contacts/search
 *   {
 *     "filterGroups": [{
 *       "filters": [{
 *         "propertyName": "hs_lastmodifieddate",
 *         "operator": "GTE",
 *         "value": 1725148800000    // 毫秒时间戳
 *       }]
 *     }],
 *     "properties": ["firstname","lastname","email","phone","jobtitle","hs_object_id","hs_lastmodifieddate"],
 *     "limit": 100,
 *     "sort": [{"propertyName": "hs_lastmodifieddate", "direction": "ASCENDING"}]
 *   }
 * </pre>
 *
 * <p>upsert：
 * <ul>
 *   <li>POST /crm/v3/objects/contacts              ── 新建（返回 id）</li>
 *   <li>PATCH /crm/v3/objects/contacts/{id}        ── 更新</li>
 *   <li>POST /crm/v3/objects/contacts/batch/upsert ── 按 email 幂等 upsert（首选）</li>
 * </ul>
 *
 * <p>字段映射（DEFAULT_FIELD_MAP）：
 * <pre>
 *   externalId  → hs_object_id       (Contact 主键)
 *   firstName   → firstname
 *   lastName    → lastname
 *   email       → email
 *   phone       → phone
 *   title       → jobtitle
 *   updatedAtMs → hs_lastmodifieddate (毫秒时间戳)
 * </pre>
 */
@Slf4j
@Component
public class HubSpotCrmAdapter implements CrmAdapter {

    @Autowired(required = false)
    private ObjectMapper jackson;

    @Override
    public String platformCode() {
        return "HUBSPOT";
    }

    // ============================================================
    //  默认字段映射（localField → externalField）
    // ============================================================
    static final Map<String, String> DEFAULT_FIELD_MAP = new LinkedHashMap<>();
    static {
        DEFAULT_FIELD_MAP.put("externalId", "hs_object_id");
        DEFAULT_FIELD_MAP.put("firstName",  "firstname");
        DEFAULT_FIELD_MAP.put("lastName",   "lastname");
        DEFAULT_FIELD_MAP.put("email",      "email");
        DEFAULT_FIELD_MAP.put("phone",      "phone");
        DEFAULT_FIELD_MAP.put("title",      "jobtitle");
        DEFAULT_FIELD_MAP.put("updatedAtMs","hs_lastmodifieddate");
    }

    /** 解析 {@code CrmIntegration.objectMappings} JSON，merge 到默认映射（JSON key 覆盖默认） */
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
                    // 未在 JSON 出现的 key 保留默认
                    for (Map.Entry<String, String> e : fallback.entrySet()) {
                        out.putIfAbsent(e.getKey(), e.getValue());
                    }
                    return out;
                }
            }
        } catch (Exception e) {
            log.warn("[CRM/HUBSPOT] objectMappings 解析失败，使用默认：{}", e.getMessage());
        }
        return fallback;
    }

    // ============================================================
    //  鉴权（PAT + OAuth2 双模式）
    // ============================================================
    @Override
    public AuthResult authenticate(CrmIntegration cfg) {
        if (cfg == null) return AuthResult.fail("CONFIG_MISSING", "配置缺失");
        String baseUrl = cfg.getApiBaseUrl() == null
                ? "https://api.hubapi.com"
                : cfg.getApiBaseUrl();

        // 1) 缓存命中
        if (cfg.getAccessToken() != null && cfg.getTokenExpireAt() != null
                && cfg.getTokenExpireAt().isAfter(java.time.LocalDateTime.now().plusMinutes(2))) {
            AuthResult r = new AuthResult();
            r.success = true;
            r.accessToken = cfg.getAccessToken();
            r.instanceUrl = baseUrl;
            r.expiresIn = (cfg.getTokenExpireAt().atZone(java.time.ZoneId.systemDefault())
                    .toInstant().toEpochMilli() - System.currentTimeMillis()) / 1000;
            return r;
        }

        // 2) 重新签发
        try {
            String tokenUrl = baseUrl + "/oauth/v1/token";
            String body = "grant_type=refresh_token"
                    + "&client_id=" + urlEncode(cfg.getClientId())
                    + "&client_secret=" + urlEncode(cfg.getClientSecret())
                    + "&refresh_token=" + urlEncode(cfg.getRefreshToken());
            String response = post(tokenUrl, body, "application/x-www-form-urlencoded",
                    null, 5000, 15000);
            JsonNode node = jackson.readTree(response);
            AuthResult r = new AuthResult();
            r.success = true;
            r.accessToken = node.path("access_token").asText();
            r.refreshToken = node.path("refresh_token").asText(cfg.getRefreshToken());
            r.expiresIn = node.path("expires_in").asLong(21600);
            r.instanceUrl = baseUrl;
            log.info("[CRM/HUBSPOT] OAuth2 refresh-token OK: expiresIn={}s", r.expiresIn);
            return r;
        } catch (Exception e) {
            log.warn("[CRM/HUBSPOT] authenticate failed: {}", e.getMessage());
            // 兜底：PAT 模式（access_token 直接当 PAT 用）
            if (cfg.getAccessToken() != null && !cfg.getAccessToken().isEmpty()) {
                AuthResult r = new AuthResult();
                r.success = true;
                r.accessToken = cfg.getAccessToken();
                r.instanceUrl = baseUrl;
                r.expiresIn = 86400L;
                return r;
            }
            return AuthResult.fail("HUBSPOT_AUTH_FAILED", e.getMessage());
        }
    }

    // ============================================================
    //  增量拉取 Contact
    // ============================================================
    @Override
    public List<ExternalContact> fetchContactsIncremental(CrmIntegration cfg, SyncCursor cursor) {
        AuthResult auth = authenticate(cfg);
        if (!auth.success) {
            log.warn("[CRM/HUBSPOT] fetchContacts skip: auth failed: {}", auth.errorMessage);
            return List.of();
        }

        Map<String, String> fieldMap = parseObjectMappings(cfg);
        String modField = fieldMap.getOrDefault("updatedAtMs", "hs_lastmodifieddate");

        long sinceMs = cursor == null || cursor.sinceEpochMs == null
                ? 0L
                : cursor.sinceEpochMs;

        String url = (auth.instanceUrl != null ? auth.instanceUrl : cfg.getApiBaseUrl())
                + "/crm/v3/objects/contacts/search";

        Map<String, Object> reqBody = new LinkedHashMap<>();
        reqBody.put("filterGroups", List.of(Map.of(
                "filters", List.of(Map.of(
                        "propertyName", modField,
                        "operator", "GTE",
                        "value", sinceMs
                ))
        )));
        reqBody.put("properties", buildProperties(fieldMap));
        reqBody.put("limit", 100);
        reqBody.put("sort", List.of(Map.of(
                "propertyName", modField,
                "direction", "ASCENDING"
        )));
        // EXT-24 known-id 差集：HubSpot 不支持 WHERE Id NOT IN，直接靠 PATCH 幂等
        if (cursor != null && cursor.knownExternalIds != null && !cursor.knownExternalIds.isEmpty()) {
            log.info("[CRM/HUBSPOT] knownIds={} (差集在 upsert 阶段去重)", cursor.knownExternalIds.size());
        }

        try {
            String body = jackson.writeValueAsString(reqBody);
            String response = post(url, body, "application/json", auth.accessToken, 5000, 30000);
            JsonNode root = jackson.readTree(response);
            JsonNode records = root.path("results");
            List<ExternalContact> out = new ArrayList<>();
            if (records.isArray()) {
                String extIdKey   = fieldMap.getOrDefault("externalId", "hs_object_id");
                String firstKey   = fieldMap.getOrDefault("firstName", "firstname");
                String lastKey    = fieldMap.getOrDefault("lastName", "lastname");
                String emailKey   = fieldMap.getOrDefault("email", "email");
                String phoneKey   = fieldMap.getOrDefault("phone", "phone");
                String titleKey   = fieldMap.getOrDefault("title", "jobtitle");
                String modKey     = fieldMap.getOrDefault("updatedAtMs", "hs_lastmodifieddate");
                for (JsonNode r : records) {
                    JsonNode props = r.path("properties");
                    if (props.isMissingNode()) props = r;
                    ExternalContact c = new ExternalContact();
                    c.externalId = textOrNull(r.path(extIdKey).isMissingNode() ? props.path(extIdKey) : r.path(extIdKey));
                    c.firstName  = textOrNull(props.path(firstKey));
                    c.lastName   = textOrNull(props.path(lastKey));
                    c.email      = textOrNull(props.path(emailKey));
                    c.phone      = textOrNull(props.path(phoneKey));
                    c.title      = textOrNull(props.path(titleKey));
                    JsonNode modNode = props.path(modKey);
                    if (modNode.isNumber()) {
                        c.updatedAtMs = modNode.asLong();
                    } else if (modNode.isTextual()) {
                        try {
                            // HubSpot hs_lastmodifieddate 毫秒字符串
                            c.updatedAtMs = Long.parseLong(modNode.asText());
                        } catch (Exception ignored) {
                            // 兼容 ISO 字符串
                            try { c.updatedAtMs = Instant.parse(modNode.asText()).toEpochMilli(); }
                            catch (Exception ignored2) {}
                        }
                    }
                    if (c.externalId != null) out.add(c);
                }
            }
            log.info("[CRM/HUBSPOT] fetchContacts: {} 条 (mapping fields={})", out.size(), fieldMap.values());
            return out;
        } catch (Exception e) {
            log.warn("[CRM/HUBSPOT] fetchContacts failed: {}", e.getMessage());
            return List.of();
        }
    }

    /** properties 数组：去重 + 包含所有映射字段 + 必备字段 */
    private List<String> buildProperties(Map<String, String> fieldMap) {
        LinkedHashSet<String> set = new LinkedHashSet<>();
        set.add("hs_object_id");
        set.add("hs_lastmodifieddate");
        for (String v : fieldMap.values()) {
            if (v != null && !v.isEmpty()) set.add(v);
        }
        return new ArrayList<>(set);
    }

    // ============================================================
    //  Upsert Contact（POST batch/upsert by email）
    // ============================================================
    @Override
    public PushResult upsertContact(CrmIntegration cfg, Map<String, Object> contact) {
        AuthResult auth = authenticate(cfg);
        if (!auth.success) return PushResult.fail(auth.errorCode, auth.errorMessage);

        Map<String, String> fieldMap = parseObjectMappings(cfg);
        String url = (auth.instanceUrl != null ? auth.instanceUrl : cfg.getApiBaseUrl())
                + "/crm/v3/objects/contacts/batch/upsert";

        Map<String, Object> props = new LinkedHashMap<>();
        putIfPresent(props, fieldMap.get("firstName"), contact.get("firstName"));
        putIfPresent(props, fieldMap.get("lastName"),  contact.get("lastName"));
        putIfPresent(props, fieldMap.get("email"),     contact.get("email"));
        putIfPresent(props, fieldMap.get("phone"),     contact.get("phone"));
        putIfPresent(props, fieldMap.get("title"),     contact.get("title"));
        // customFields
        Object custom = contact.get("customFields");
        if (custom instanceof Map) {
            ((Map<?, ?>) custom).forEach((k, v) -> props.put(String.valueOf(k), v));
        }
        if (props.isEmpty()) return PushResult.fail("EMPTY_PAYLOAD", "无可写入字段");

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("inputs", List.of(Map.of(
                "properties", props,
                "idProperty", "email"        // 按 email 幂等
        )));

        try {
            String response = post(url, jackson.writeValueAsString(body),
                    "application/json", auth.accessToken, 5000, 15000);
            JsonNode root = jackson.readTree(response);
            JsonNode results = root.path("results");
            if (results.isArray() && results.size() > 0) {
                JsonNode first = results.get(0);
                String id = first.path("id").asText(null);
                String etag = first.path("properties").path("hs_lastmodifieddate").asText("v1");
                return PushResult.ok(id, etag);
            }
            return PushResult.fail("HUBSPOT_EMPTY_RESPONSE", response);
        } catch (Exception e) {
            return PushResult.fail("HUBSPOT_UPSERT_FAILED", e.getMessage());
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
        // 轻量探活：访问 GET /crm/v3/objects/contacts?limit=1
        try {
            String url = (auth.instanceUrl != null ? auth.instanceUrl : cfg.getApiBaseUrl())
                    + "/crm/v3/objects/contacts?limit=1";
            get(url, auth.accessToken);
            s.healthy = true;
            s.stage = "AUTH_AND_QUERY_OK";
            s.message = "HubSpot 鉴权 + 查询通过";
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
    //  HTTP 工具（与 Salesforce 共用风格）
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
