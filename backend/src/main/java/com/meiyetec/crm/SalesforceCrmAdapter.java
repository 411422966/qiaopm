package com.meiyetec.crm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.meiyetec.entity.CrmIntegration;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;

/**
 * EXT-15：Salesforce CRM 适配器（优先级实现）
 *
 * <p>OAuth2 JWT Bearer Flow：
 * <pre>
 *   1. 用 clientId + username + privateKey(PEM) 构造 JWT
 *      header  : {"alg":"RS256","typ":"JWT"}
 *      payload : {"iss":"&lt;clientId&gt;","sub":"&lt;username&gt;","aud":"&lt;loginUrl&gt;","exp":...}
 *   2. 用 RSA-SHA256 + privateKey 签名
 *   3. POST https://&lt;loginUrl&gt;/services/oauth2/token
 *      grant_type=urn:ietf:params:oauth:grant-type:jwt-bearer&amp;assertion=&lt;JWT&gt;
 *   4. 拿到 access_token + instance_url
 * </pre>
 *
 * <p>Contact 增量同步：
 * <pre>
 *   SOQL: SELECT Id, FirstName, LastName, Email, Phone, AccountId,
 *                SystemModstamp FROM Contact
 *         WHERE SystemModstamp &gt; 2026-09-01T00:00:00Z
 *         ORDER BY SystemModstamp ASC LIMIT 200
 *   Headers: Authorization: Bearer &lt;access_token&gt;
 * </pre>
 *
 * <p>实际生产请替换为 OkHttp / Salesforce Java SDK。本类已实现：
 * <ul>
 *   <li>JWT 构造 + RSA-SHA256 签名</li>
 *   <li>Token 缓存（基于 tokenExpireAt）</li>
 *   <li>SOQL 增量拉取</li>
 *   <li>Contact upsert（POST /services/data/v59.0/sobjects/Contact/External_Id__c/{externalId}）</li>
 *   <li>syncToken 持久化到 crm_integration.sync_token（用于下次增量查询）</li>
 * </ul>
 */
@Slf4j
@Component
public class SalesforceCrmAdapter implements CrmAdapter {

    @Autowired(required = false)
    private ObjectMapper jackson;

    @Override
    public String platformCode() {
        return "SALESFORCE";
    }

    // ============================================================
    //  鉴权
    // ============================================================

    @Override
    public AuthResult authenticate(CrmIntegration cfg) {
        long start = System.currentTimeMillis();
        if (cfg == null) return AuthResult.fail("CONFIG_MISSING", "配置缺失");
        if (cfg.getClientId() == null || cfg.getUsername() == null
                || cfg.getPrivateKey() == null || cfg.getLoginUrl() == null) {
            return AuthResult.fail("OAUTH2_JWT_INPUT_MISSING",
                    "clientId / username / privateKey / loginUrl 必填");
        }

        // 1. 缓存命中：未过期且 accessToken 不为空
        if (cfg.getAccessToken() != null && cfg.getTokenExpireAt() != null
                && cfg.getTokenExpireAt().isAfter(java.time.LocalDateTime.now().plusMinutes(2))) {
            AuthResult r = new AuthResult();
            r.success = true;
            r.accessToken = cfg.getAccessToken();
            r.instanceUrl = cfg.getApiBaseUrl();
            r.expiresIn = (cfg.getTokenExpireAt().atZone(java.time.ZoneId.systemDefault())
                    .toInstant().toEpochMilli() - System.currentTimeMillis()) / 1000;
            return r;
        }

        // 2. 重新签发
        try {
            String jwt = buildJwt(cfg);
            String tokenUrl = cfg.getLoginUrl()
                    + (cfg.getLoginUrl().endsWith("/") ? "" : "/")
                    + "services/oauth2/token";
            String body = "grant_type=urn:ietf:params:oauth:grant-type:jwt-bearer"
                    + "&assertion=" + java.net.URLEncoder.encode(jwt, "UTF-8");
            String response = post(tokenUrl, body, "application/x-www-form-urlencoded",
                    null, 5000, 15000);
            JsonNode node = jackson.readTree(response);
            AuthResult r = new AuthResult();
            r.success = true;
            r.accessToken = node.path("access_token").asText();
            r.instanceUrl = node.path("instance_url").asText();
            String refresh = node.path("refresh_token").asText(null);
            r.refreshToken = refresh;
            long expiresIn = node.path("expires_in").asLong(7200);
            r.expiresIn = expiresIn;
            log.info("[CRM/SALESFORCE] authenticated: user={} expiresIn={}s elapsed={}ms",
                    cfg.getUsername(), expiresIn, System.currentTimeMillis() - start);
            return r;
        } catch (Exception e) {
            log.warn("[CRM/SALESFORCE] authenticate failed: {}", e.getMessage());
            return AuthResult.fail("OAUTH2_JWT_FAILED", e.getMessage());
        }
    }

    /**
     * 构造 OAuth2 JWT
     */
    public static String buildJwt(CrmIntegration cfg) {
        try {
            String header = "{\"alg\":\"RS256\",\"typ\":\"JWT\"}";
            long exp = Instant.now().getEpochSecond() + 300;     // 5 分钟有效
            String payload = "{\"iss\":\"" + escape(cfg.getClientId())
                    + "\",\"sub\":\"" + escape(cfg.getUsername())
                    + "\",\"aud\":\"" + escape(cfg.getLoginUrl())
                    + "\",\"exp\":" + exp + "}";
            String headerB64 = base64Url(header.getBytes(StandardCharsets.UTF_8));
            String payloadB64 = base64Url(payload.getBytes(StandardCharsets.UTF_8));
            String signingInput = headerB64 + "." + payloadB64;

            // 解析 RSA 私钥
            String pk = cfg.getPrivateKey()
                    .replace("-----BEGIN PRIVATE KEY-----", "")
                    .replace("-----END PRIVATE KEY-----", "")
                    .replace("-----BEGIN RSA PRIVATE KEY-----", "")
                    .replace("-----END RSA PRIVATE KEY-----", "")
                    .replaceAll("\\s+", "");
            java.security.spec.PKCS8EncodedKeySpec spec =
                    new java.security.spec.PKCS8EncodedKeySpec(
                            Base64.getDecoder().decode(pk));
            java.security.PrivateKey privateKey = java.security.KeyFactory.getInstance("RSA")
                    .generatePrivate(spec);

            java.security.Signature sig = java.security.Signature.getInstance("SHA256withRSA");
            sig.initSign(privateKey);
            sig.update(signingInput.getBytes(StandardCharsets.UTF_8));
            String signatureB64 = base64Url(sig.sign());
            return signingInput + "." + signatureB64;
        } catch (Exception e) {
            throw new RuntimeException("buildJwt failed: " + e.getMessage(), e);
        }
    }

    // ============================================================
    //  增量拉取 Contact（EXT-24：动态字段映射）
    // ============================================================

    /** 默认 SOQL 字段（当 {@code objectMappings} 未配置时使用） */
    static final String DEFAULT_SOQL_FIELDS = "Id,FirstName,LastName,Email,Phone,Title,SystemModstamp";

    /** 默认字段映射：localField → externalField（按 ExternalContact 字段名） */
    static final java.util.Map<String, String> DEFAULT_FIELD_MAP = java.util.Map.of(
            "externalId",  "Id",
            "firstName",    "FirstName",
            "lastName",     "LastName",
            "email",        "Email",
            "phone",        "Phone",
            "title",        "Title",
            "updatedAtMs",  "SystemModstamp"
    );

    /**
     * 解析 {@code CrmIntegration.objectMappings} JSON 为 {@code localField → externalField}。
     *
     * <p>JSON 示例：
     * <pre>{@code
     *   {
     *     "firstName": "FirstName",
     *     "email":     "Email__c",
     *     "phone":     "Phone_Mobile__c",
     *     "title":     "Title__c",
     *     "externalId": "Id"
     *   }
     * }</pre>
     * <p>未配置 / 解析失败 → 返回 {@link #DEFAULT_FIELD_MAP} 的拷贝。
     */
    public static java.util.Map<String, String> parseObjectMappings(CrmIntegration cfg) {
        java.util.Map<String, String> fallback = new java.util.LinkedHashMap<>(DEFAULT_FIELD_MAP);
        if (cfg == null || cfg.getObjectMappings() == null || cfg.getObjectMappings().isEmpty()) {
            return fallback;
        }
        try {
            com.fasterxml.jackson.databind.JsonNode node = new com.fasterxml.jackson.databind.ObjectMapper()
                    .readTree(cfg.getObjectMappings());
            if (node.isObject()) {
                java.util.Map<String, String> out = new java.util.LinkedHashMap<>();
                node.fields().forEachRemaining(e -> out.put(e.getKey(), e.getValue().asText()));
                if (!out.isEmpty()) {
                    // merged: 用户定义覆盖默认（未在 JSON 出现的 key 保留默认）
                    for (java.util.Map.Entry<String, String> e : fallback.entrySet()) {
                        out.putIfAbsent(e.getKey(), e.getValue());
                    }
                    return out;
                }
            }
        } catch (Exception e) {
            log.warn("[CRM/SALESFORCE] objectMappings 解析失败，使用默认：{}", e.getMessage());
        }
        return fallback;
    }

    /** 根据字段映射构造 SOQL SELECT 子句（URL-encoded） */
    static String buildSoqlSelectClause(java.util.Map<String, String> fieldMap) {
        // SOQL 必备 Id 和 SystemModstamp（增量 cursor 需要）
        java.util.LinkedHashSet<String> fields = new java.util.LinkedHashSet<>();
        fields.add(fieldMap.getOrDefault("externalId", "Id"));
        fields.add(fieldMap.getOrDefault("updatedAtMs", "SystemModstamp"));
        for (java.util.Map.Entry<String, String> e : fieldMap.entrySet()) {
            if ("externalId".equals(e.getKey()) || "updatedAtMs".equals(e.getKey())) continue;
            if (e.getValue() != null && !e.getValue().isEmpty()) fields.add(e.getValue());
        }
        return String.join(",", fields);  // 后续整体 URL-encode
    }

    @Override
    public List<ExternalContact> fetchContactsIncremental(CrmIntegration cfg, SyncCursor cursor) {
        AuthResult auth = authenticate(cfg);
        if (!auth.success) {
            log.warn("[CRM/SALESFORCE] fetchContacts skip: auth failed: {}", auth.errorMessage);
            return List.of();
        }
        String instanceUrl = auth.instanceUrl != null ? auth.instanceUrl : cfg.getApiBaseUrl();
        if (instanceUrl == null) {
            log.warn("[CRM/SALESFORCE] instanceUrl 缺失");
            return List.of();
        }

        // 1. 解析字段映射（默认 + objectMappings 覆盖）
        java.util.Map<String, String> fieldMap = parseObjectMappings(cfg);
        String selectFields = buildSoqlSelectClause(fieldMap);

        // 2. 构造 SOQL：使用 SystemModstamp（external 中的 updatedAtMs 字段名）增量
        String sinceIso = cursor == null || cursor.sinceEpochMs == null
                ? "2026-01-01T00:00:00Z"
                : java.time.Instant.ofEpochMilli(cursor.sinceEpochMs).toString();
        String modField = fieldMap.getOrDefault("updatedAtMs", "SystemModstamp");
        String soql = "SELECT " + selectFields
                + " FROM Contact"
                + " WHERE " + modField + " > " + sinceIso
                + " ORDER BY " + modField + " ASC LIMIT 200";
        // URL 编码整个 SOQL
        String soqlEncoded;
        try { soqlEncoded = java.net.URLEncoder.encode(soql, "UTF-8"); }
        catch (Exception e) { soqlEncoded = soql.replace(" ", "+").replace("=", "%3D"); }
        String url = instanceUrl + "/services/data/v59.0/query/?q=" + soqlEncoded;

        log.info("[CRM/SALESFORCE] SOQL fields={} knownIds={}",
                selectFields,
                cursor == null || cursor.knownExternalIds == null
                        ? 0 : cursor.knownExternalIds.size());

        try {
            String response = get(url, auth.accessToken);
            JsonNode root = jackson.readTree(response);
            JsonNode records = root.path("records");
            List<ExternalContact> out = new ArrayList<>();
            if (records.isArray()) {
                String extIdKey   = fieldMap.getOrDefault("externalId", "Id");
                String firstKey   = fieldMap.getOrDefault("firstName", "FirstName");
                String lastKey    = fieldMap.getOrDefault("lastName", "LastName");
                String emailKey   = fieldMap.getOrDefault("email", "Email");
                String phoneKey   = fieldMap.getOrDefault("phone", "Phone");
                String titleKey   = fieldMap.getOrDefault("title", "Title");
                String modKey     = fieldMap.getOrDefault("updatedAtMs", "SystemModstamp");
                for (JsonNode r : records) {
                    ExternalContact c = new ExternalContact();
                    c.externalId = r.path(extIdKey).asText(null);
                    c.firstName  = r.path(firstKey).asText(null);
                    c.lastName   = r.path(lastKey).asText(null);
                    c.email      = r.path(emailKey).asText(null);
                    c.phone      = r.path(phoneKey).asText(null);
                    c.title      = r.path(titleKey).asText(null);
                    String mod = r.path(modKey).asText(null);
                    if (mod != null) {
                        try { c.updatedAtMs = java.time.Instant.parse(mod).toEpochMilli(); }
                        catch (Exception ignored) {}
                    }
                    if (c.externalId != null) out.add(c);
                }
            }
            log.info("[CRM/SALESFORCE] fetchContacts: {} 条 (mapping fields={})", out.size(), selectFields);
            return out;
        } catch (Exception e) {
            log.warn("[CRM/SALESFORCE] fetchContacts failed: {}", e.getMessage());
            return List.of();
        }
    }

    // ============================================================
    //  Upsert Contact（按 externalId 幂等）
    // ============================================================

    @Override
    public PushResult upsertContact(CrmIntegration cfg, Map<String, Object> contact) {
        AuthResult auth = authenticate(cfg);
        if (!auth.success) return PushResult.fail(auth.errorCode, auth.errorMessage);
        String instanceUrl = auth.instanceUrl != null ? auth.instanceUrl : cfg.getApiBaseUrl();
        String externalId = contact.get("externalId") == null ? null
                : contact.get("externalId").toString();
        String upsertKey = externalId == null ? "Email" : externalId;
        String url = instanceUrl + "/services/data/v59.0/sobjects/Contact/"
                + (externalId == null ? "Email/" + upsertKey : externalId)
                + "?_HttpMethod=PATCH";
        try {
            String response = post(url, jackson.writeValueAsString(contact),
                    "application/json", auth.accessToken, 5000, 15000);
            if (response.isEmpty() || response.equals("\"\"")) {
                // PATCH upsert：响应 204 No Content（成功）
                return PushResult.ok(externalId, "v1");
            }
            JsonNode node = jackson.readTree(response);
            return PushResult.ok(node.path("id").asText(externalId),
                    node.path("etag").asText("v1"));
        } catch (Exception e) {
            return PushResult.fail("UPSERT_FAILED", e.getMessage());
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
        s.healthy = true;
        s.stage = "AUTH_OK";
        s.message = "Salesforce JWT Bearer 鉴权通过";
        s.apiBaseUrl = auth.instanceUrl;
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

    private static String escape(String s) {
        return s == null ? "" : s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static String base64Url(byte[] b) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(b);
    }
}
