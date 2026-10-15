package com.meiyetec.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.meiyetec.entity.*;
import com.meiyetec.exception.BizCode;
import com.meiyetec.exception.BusinessException;
import com.meiyetec.mapper.*;
import com.meiyetec.security.TenantContextHolder;
import com.meiyetec.util.Keys;
import com.meiyetec.util.Result;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * R3.0-H P2.1：开放平台补强（HMAC 签名 + 重试 + 限流 + 密钥轮换 + 事件订阅）
 *
 * <p>在 P3EcosystemService 已有基础上补强：
 * <ul>
 *   <li>Webhook HMAC-SHA256 签名 + 验签</li>
 *   <li>Webhook 重试 + 死信队列</li>
 *   <li>Open API 限流（MINUTE/HOUR/DAY/MONTH 滑动窗口）</li>
 *   <li>Open App 密钥轮换（带审计）</li>
 *   <li>Webhook 事件订阅（多租户，多订阅）</li>
 * </ul>
 */
@Slf4j
@RestController
@RequestMapping("/open/api/v2")
@RequiredArgsConstructor
public class OpenPlatformEnhanceService {

    private final PlatformWebhookEventMapper eventMapper;
    private final PlatformWebhookSubscriptionMapper subMapper;
    private final PlatformWebhookDeliveryMapper deliveryMapper;
    private final OpenApiQuotaMapper quotaMapper;
    private final OpenAppSecretAuditMapper auditMapper;

    @Autowired
    private JdbcTemplate jdbc;

    private static final SecureRandom RNG = new SecureRandom();

    // 内存级限流缓存（生产建议用 Redis）
    private static final Map<String, Long> inMemWindow = new ConcurrentHashMap<>();

    // ====================================================================
    // 1. HMAC-SHA256 签名工具
    // ====================================================================

    public static String hmacSha256(String secret, String payload) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            SecretKeySpec keySpec = new SecretKeySpec(
                    secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            mac.init(keySpec);
            byte[] result = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            return "sha256=" + HexFormat.of().formatHex(result);
        } catch (Exception e) {
            throw new RuntimeException("HMAC 签名失败", e);
        }
    }

    public static boolean verifyHmac(String secret, String payload, String signature) {
        if (signature == null) return false;
        String expected = hmacSha256(secret, payload);
        // 常时间比较防止时序攻击
        return constantTimeEquals(expected, signature);
    }

    private static boolean constantTimeEquals(String a, String b) {
        if (a == null || b == null || a.length() != b.length()) return false;
        int result = 0;
        for (int i = 0; i < a.length(); i++) result |= a.charAt(i) ^ b.charAt(i);
        return result == 0;
    }

    public static String sha256Hex(String s) {
        try {
            byte[] b = MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(b);
        } catch (Exception e) {
            return Integer.toHexString(s.hashCode());
        }
    }

    // ====================================================================
    // 2. Webhook 订阅管理
    // ====================================================================

    @PostMapping("/webhook/subscription")
    @Transactional(rollbackFor = Exception.class)
    public Result<PlatformWebhookSubscription> createSubscription(@RequestBody Map<String, Object> body) {
        Long tenantId = TenantContextHolder.get().getTenantId();
        PlatformWebhookSubscription s = new PlatformWebhookSubscription();
        s.setTenantId(tenantId);
        s.setName((String) body.get("name"));
        s.setTargetUrl((String) body.get("targetUrl"));
        s.setSigningSecret(generateSecret(32));
        s.setEventsCsv((String) body.getOrDefault("events", "*"));
        s.setEnabled(body.get("enabled") == null ? 1 : ((Number) body.get("enabled")).intValue());
        s.setRetryStrategy((String) body.getOrDefault("retryStrategy", "EXP_BACKOFF"));
        s.setMaxAttempts(body.get("maxAttempts") == null ? 5 : ((Number) body.get("maxAttempts")).intValue());
        s.setTimeoutSeconds(body.get("timeoutSeconds") == null ? 10
                : ((Number) body.get("timeoutSeconds")).intValue());
        s.setDescription((String) body.get("description"));
        s.setOperatorName((String) body.getOrDefault("operatorName", "admin"));
        s.setSuccessCount(0L);
        s.setFailureCount(0L);
        subMapper.insert(s);
        return Result.success("已创建（signingSecret 仅返回一次）", s);
    }

    @GetMapping("/webhook/subscription/page")
    public Result<com.baomidou.mybatisplus.extension.plugins.pagination.Page<Map<String, Object>>> subPage(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) Integer enabled) {
        Long tenantId = TenantContextHolder.get().getTenantId();
        StringBuilder sql = new StringBuilder(
            "SELECT id, name, target_url, events_csv, enabled, retry_strategy, " +
            "max_attempts, timeout_seconds, last_triggered_at, success_count, failure_count, " +
            "create_time FROM platform_webhook_subscription WHERE deleted=0 AND tenant_id=? ");
        List<Object> args = new ArrayList<>();
        args.add(tenantId);
        if (enabled != null) { sql.append("AND enabled=? "); args.add(enabled); }
        sql.append("ORDER BY id DESC LIMIT ? OFFSET ?");
        args.add(pageSize); args.add((pageNum - 1) * pageSize);

        StringBuilder csql = new StringBuilder(
            "SELECT COUNT(*) FROM platform_webhook_subscription WHERE deleted=0 AND tenant_id=? ");
        List<Object> cargs = new ArrayList<>(args.subList(0, args.size() - 2));
        if (enabled != null) csql.append("AND enabled=? ");
        Long total = jdbc.queryForObject(csql.toString(), Long.class, cargs.toArray());

        List<Map<String, Object>> records = jdbc.queryForList(sql.toString(), args.toArray());
        com.baomidou.mybatisplus.extension.plugins.pagination.Page<Map<String, Object>> page =
                new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(pageNum, pageSize);
        page.setRecords(Keys.camel(records));
        page.setTotal(total);
        return Result.success(page);
    }

    @PostMapping("/webhook/subscription/{id}/rotate-secret")
    @Transactional(rollbackFor = Exception.class)
    public Result<PlatformWebhookSubscription> rotateSecret(@PathVariable Long id) {
        PlatformWebhookSubscription s = subMapper.selectById(id);
        if (s == null) return Result.error(BizCode.NOT_FOUND, "订阅不存在");
        String oldSecret = s.getSigningSecret();
        s.setSigningSecret(generateSecret(32));
        subMapper.updateById(s);

        // 审计
        OpenAppSecretAudit a = new OpenAppSecretAudit();
        a.setTenantId(s.getTenantId());
        a.setAppId(id);                 // 复用 appId 字段存 subscription_id
        a.setOldSecretPrefix(oldSecret.length() > 8 ? oldSecret.substring(0, 8) : oldSecret);
        a.setNewSecretPrefix(s.getSigningSecret().substring(0, 8));
        a.setSecretHash(sha256Hex(s.getSigningSecret()));
        a.setAction("ROTATE");
        a.setOperatorName("admin");
        auditMapper.insert(a);

        return Result.success("已轮换（新 secret 仅返回一次，请更新订阅方）", s);
    }

    // ====================================================================
    // 3. Webhook 事件分发（带 HMAC 签名 + 投递记录）
    // ====================================================================

    @PostMapping("/webhook/dispatch")
    @Transactional(rollbackFor = Exception.class)
    public Result<Map<String, Object>> dispatch(@RequestBody Map<String, Object> body) {
        Long tenantId = TenantContextHolder.get().getTenantId();
        String event = (String) body.get("event");
        Object payload = body.get("payload");
        if (event == null || payload == null)
            return Result.error(BizCode.BAD_REQUEST, "event / payload 必填");

        String payloadStr = payload.toString();
        String hash = sha256Hex(payloadStr);

        // 找该事件的所有订阅
        List<PlatformWebhookSubscription> subs = subMapper.selectList(
                new LambdaQueryWrapper<PlatformWebhookSubscription>()
                        .eq(PlatformWebhookSubscription::getTenantId, tenantId)
                        .eq(PlatformWebhookSubscription::getEnabled, 1));
        int matched = 0;
        for (PlatformWebhookSubscription s : subs) {
            if (!subscribesTo(s, event)) continue;
            matched++;
            // 创建投递记录 + 签名
            PlatformWebhookDelivery d = new PlatformWebhookDelivery();
            d.setTenantId(tenantId);
            d.setSubscriptionId(s.getId());
            d.setEventName(event);
            d.setPayload(payloadStr);
            d.setPayloadHash(hash);
            d.setTargetUrl(s.getTargetUrl());
            d.setSigningSecret(s.getSigningSecret());
            d.setSignature(hmacSha256(s.getSigningSecret(), payloadStr));
            d.setAttempt(1);
            d.setStatus("PENDING");
            deliveryMapper.insert(d);
            // 真实生产：异步任务调度去推送
            // 这里仅返回签名 + 投递 ID，供测试 / 调试
        }
        Map<String, Object> data = new HashMap<>();
        data.put("matchedSubscriptions", matched);
        data.put("eventHash", hash);
        return Result.success("已分发到 " + matched + " 个订阅", data);
    }

    /** 订阅是否包含该事件 */
    private boolean subscribesTo(PlatformWebhookSubscription s, String event) {
        if (s.getEventsCsv() == null || s.getEventsCsv().isEmpty()) return false;
        if ("*".equals(s.getEventsCsv().trim())) return true;
        for (String e : s.getEventsCsv().split(",")) {
            if (e.trim().equals(event)) return true;
        }
        return false;
    }

    /**
     * 投递重试（手动触发 / 调度器调用）
     */
    @PostMapping("/webhook/delivery/retry")
    @Transactional(rollbackFor = Exception.class)
    public Result<Map<String, Object>> retryDelivery(@RequestBody Map<String, Object> body) {
        Long deliveryId = body.get("deliveryId") == null ? null
                : ((Number) body.get("deliveryId")).longValue();
        Integer httpStatus = body.get("httpStatus") == null ? 0
                : ((Number) body.get("httpStatus")).intValue();
        String responseBody = (String) body.get("responseBody");

        if (deliveryId == null) return Result.error(BizCode.BAD_REQUEST, "deliveryId 必填");
        PlatformWebhookDelivery d = deliveryMapper.selectById(deliveryId);
        if (d == null) return Result.error(BizCode.NOT_FOUND, "投递记录不存在");

        boolean success = httpStatus >= 200 && httpStatus < 300;
        if (success) {
            d.setStatus("SUCCESS");
            d.setHttpStatus(httpStatus);
            d.setResponseBody(responseBody);
            deliveryMapper.updateById(d);
            jdbc.update("UPDATE platform_webhook_subscription SET success_count=success_count+1, last_triggered_at=NOW() WHERE id=?",
                    d.getSubscriptionId());
            return Result.success("已确认成功", null);
        }

        // 失败：决定是否重试
        PlatformWebhookSubscription s = subMapper.selectById(d.getSubscriptionId());
        int maxAttempts = s == null ? 5 : (s.getMaxAttempts() == null ? 5 : s.getMaxAttempts());

        d.setAttempt(d.getAttempt() + 1);
        d.setHttpStatus(httpStatus);
        d.setResponseBody(responseBody);

        if (d.getAttempt() >= maxAttempts) {
            d.setStatus("DEAD_LETTER");
            deliveryMapper.updateById(d);
            jdbc.update("UPDATE platform_webhook_subscription SET failure_count=failure_count+1 WHERE id=?",
                    d.getSubscriptionId());
            return Result.success("已达最大重试次数，进入死信队列", null);
        }

        // 指数退避：30s × 2^(attempt-1)，最多 60 分钟
        long delaySec = Math.min(3600L, 30L * (1L << (d.getAttempt() - 1)));
        d.setNextRetryAt(LocalDateTime.now().plusSeconds(delaySec));
        d.setStatus("FAILED");
        deliveryMapper.updateById(d);
        Map<String, Object> data = new HashMap<>();
        data.put("nextRetryAt", d.getNextRetryAt());
        data.put("attempt", d.getAttempt());
        data.put("maxAttempts", maxAttempts);
        return Result.success("已安排重试", data);
    }

    /** 死信队列列表 */
    @GetMapping("/webhook/delivery/dead-letter")
    public Result<List<Map<String, Object>>> deadLetter(
            @RequestParam(defaultValue = "50") int limit) {
        Long tenantId = TenantContextHolder.get().getTenantId();
        List<Map<String, Object>> rows = jdbc.queryForList(
            "SELECT id, subscription_id, event_name, target_url, attempt, http_status, " +
            "       response_body, created_at, updated_at " +
            "FROM platform_webhook_delivery " +
            "WHERE tenant_id = ? AND status = 'DEAD_LETTER' " +
            "ORDER BY updated_at DESC LIMIT ?",
            tenantId, limit);
        return Result.success(Keys.camel(rows));
    }

    // ====================================================================
    // 4. API 限流（滑动窗口）
    // ====================================================================

    /**
     * 检查并扣减配额；返回 remaining / blocked
     */
    public Map<String, Object> checkAndConsume(Long appId, String window, int limit) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime windowStart, windowEnd;
        switch (window) {
            case "MINUTE" -> {
                windowStart = now.withSecond(0).withNano(0);
                windowEnd = windowStart.plusMinutes(1);
            }
            case "HOUR" -> {
                windowStart = now.withMinute(0).withSecond(0).withNano(0);
                windowEnd = windowStart.plusHours(1);
            }
            case "DAY" -> {
                windowStart = now.toLocalDate().atStartOfDay();
                windowEnd = windowStart.plusDays(1);
            }
            case "MONTH" -> {
                windowStart = now.toLocalDate().withDayOfMonth(1).atStartOfDay();
                windowEnd = windowStart.plusMonths(1);
            }
            default -> throw new BusinessException(BizCode.BAD_REQUEST, "未知窗口：" + window);
        }

        // 用 JdbcTemplate 实现 CAS 扣减
        int updated = jdbc.update(
            "INSERT INTO open_api_quota (tenant_id, app_id, window, quota_limit, used_count, window_start, window_end) " +
            "VALUES (?, ?, ?, ?, 1, ?, ?) " +
            "ON DUPLICATE KEY UPDATE " +
            "  used_count = IF(window_end <= NOW(), 1, used_count + 1)",
            TenantContextHolder.get().getTenantId(), appId, window, limit, windowStart, windowEnd);

        Integer used = jdbc.queryForObject(
            "SELECT used_count FROM open_api_quota WHERE app_id=? AND window=? AND window_start=?",
            Integer.class, appId, window, windowStart);

        Map<String, Object> res = new HashMap<>();
        res.put("used", used == null ? 0 : used);
        res.put("limit", limit);
        res.put("remaining", Math.max(0, limit - (used == null ? 0 : used)));
        res.put("blocked", used != null && used > limit);
        res.put("window", window);
        res.put("windowStart", windowStart);
        res.put("windowEnd", windowEnd);
        return res;
    }

    @GetMapping("/quota/status")
    public Result<Map<String, Object>> quotaStatus(@RequestParam Long appId) {
        Map<String, Object> data = new LinkedHashMap<>();
        for (String w : new String[]{"MINUTE", "HOUR", "DAY", "MONTH"}) {
            int limit = switch (w) {
                case "MINUTE" -> 60;
                case "HOUR" -> 1000;
                case "DAY" -> 20000;
                case "MONTH" -> 500000;
                default -> 0;
            };
            data.put(w, checkAndConsume(appId, w, limit));
        }
        return Result.success(data);
    }

    // ====================================================================
    // 5. 密钥轮换审计
    // ====================================================================

    @GetMapping("/secret/audit")
    public Result<List<OpenAppSecretAudit>> secretAudit(@RequestParam Long appId) {
        return Result.success(auditMapper.selectList(
                new LambdaQueryWrapper<OpenAppSecretAudit>()
                        .eq(OpenAppSecretAudit::getAppId, appId)
                        .orderByDesc(OpenAppSecretAudit::getCreatedAt)));
    }

    @PostMapping("/app/{id}/rotate")
    @Transactional(rollbackFor = Exception.class)
    public Result<Map<String, Object>> rotateAppSecret(@PathVariable Long id,
                                                       @RequestBody(required = false) Map<String, Object> body) {
        // T07-D1：从 TenantContextHolder 取租户；缺失则拒绝，禁止 fallback 到默认租户 1L
        Long tenantId = TenantContextHolder.get().getTenantId();
        if (tenantId == null) {
            return Result.error(BizCode.UNAUTHORIZED, "未识别租户");
        }
        // 复用 OpenApp Mapper（plugin 没有就用 jdbc）
        Map<String, Object> old = jdbc.queryForList(
            "SELECT id, app_secret FROM open_app WHERE id = ?", id)
            .stream().findFirst().orElse(null);
        if (old == null) return Result.error(BizCode.NOT_FOUND, "App 不存在");

        String oldSecret = (String) old.get("app_secret");
        String newSecret = generateSecret(24);
        jdbc.update("UPDATE open_app SET app_secret = ? WHERE id = ?", newSecret, id);

        OpenAppSecretAudit a = new OpenAppSecretAudit();
        a.setTenantId(tenantId);
        a.setAppId(id);
        a.setOldSecretPrefix(oldSecret.length() > 8 ? oldSecret.substring(0, 8) : oldSecret);
        a.setNewSecretPrefix(newSecret.substring(0, 8));
        a.setSecretHash(sha256Hex(newSecret));
        a.setAction("ROTATE");
        a.setOperatorName((String) body.getOrDefault("operatorName", "admin"));
        auditMapper.insert(a);

        Map<String, Object> data = new HashMap<>();
        data.put("appId", id);
        data.put("newSecret", newSecret);
        data.put("warning", "新 secret 仅返回一次，请妥善保存");
        return Result.success("已轮换", data);
    }

    // ====================================================================
    // 6. 事件类型管理
    // ====================================================================

    @GetMapping("/webhook/events")
    public Result<List<PlatformWebhookEvent>> listEvents() {
        return Result.success(eventMapper.selectList(
                new LambdaQueryWrapper<PlatformWebhookEvent>()
                        .eq(PlatformWebhookEvent::getTenantId,
                                TenantContextHolder.get().getTenantId())
                        .eq(PlatformWebhookEvent::getEnabled, 1)
                        .orderByAsc(PlatformWebhookEvent::getId)));
    }

    @PostMapping("/webhook/events")
    public Result<PlatformWebhookEvent> createEvent(@RequestBody PlatformWebhookEvent e) {
        e.setTenantId(TenantContextHolder.get().getTenantId());
        if (e.getEnabled() == null) e.setEnabled(1);
        eventMapper.insert(e);
        return Result.success("已创建", e);
    }

    // ====================================================================
    // 工具
    // ====================================================================

    private static String generateSecret(int bytes) {
        byte[] b = new byte[bytes];
        RNG.nextBytes(b);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(b);
    }
}