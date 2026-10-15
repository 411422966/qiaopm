package com.meiyetec.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.meiyetec.ecommerce.YouzanEcommerceAdapter;
import com.meiyetec.entity.EcommerceOrder;
import com.meiyetec.entity.EcommercePlatform;
import com.meiyetec.entity.WebhookMsgLog;
import com.meiyetec.exception.BizCode;
import com.meiyetec.mapper.EcommerceOrderMapper;
import com.meiyetec.mapper.EcommercePlatformMapper;
import com.meiyetec.mapper.WebhookMsgLogMapper;
import com.meiyetec.util.Result;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.ContentCachingRequestWrapper;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * EXT-24：有赞（Youzan）Webhook 接收端
 * <p>路由：{@code POST /webhook/youzan?kdt_id={kdtId}}
 * <p>签名头：{@code X-Youzan-Sign} = md5(signSecret + rawBody)，全小写。
 *
 * <h3>处理流程</h3>
 * <ol>
 *   <li>{@link ContentCachingRequestWrapper} 读原始 body（避免 Jackson 重排破坏签名）</li>
 *   <li>按 {@code kdt_id} 反查 {@code ecommerce_platform.platform = 'YOUZAN'}</li>
 *   <li>调 {@link YouzanEcommerceAdapter#verifyWebhookSignature} 验签</li>
 *   <li>用 msg_id 在 {@code webhook_msg_log} 表判重（重复直接幂等成功）</li>
 *   <li>解析 JSON → upsert {@code ecommerce_order}</li>
 *   <li>写 webhook_msg_log（status=PROCESSED / FAILED）</li>
 * </ol>
 *
 * <h3>响应规范</h3>
 * <p>统一 HTTP 200 + {@link Result} 错误码（与项目其他端点一致）：
 * <ul>
 *   <li>code=200 → 处理成功 / 幂等命中</li>
 *   <li>code=404 → 平台配置缺失</li>
 *   <li>code=403 → 签名不匹配</li>
 *   <li>code=500 → 解析 / 写库异常</li>
 * </ul>
 */
@Slf4j
@RestController
@RequiredArgsConstructor
public class YouzanWebhookController {

    private final EcommercePlatformMapper platformMapper;
    private final EcommerceOrderMapper orderMapper;
    private final WebhookMsgLogMapper webhookMsgLogMapper;

    @Autowired(required = false)
    private ObjectMapper jackson;

    /**
     * 接收有赞 webhook 推送。
     *
     * @param kdtId     店铺 ID（query 参数，必填）
     * @param signature X-Youzan-Sign 头值（必填）
     * @param request   原始 request（用 ContentCachingRequestWrapper 包一层）
     */
    @PostMapping("/webhook/youzan")
    @Transactional(rollbackFor = Exception.class)
    public ResponseEntity<Result<Map<String, Object>>> HandleYouzan(
            @RequestParam(value = "kdt_id", required = false) String kdtId,
            @RequestHeader(value = "X-Youzan-Sign", required = false) String signature,
            HttpServletRequest request) {

        long t0 = System.currentTimeMillis();
        try {
            return handleYouzanInternal(t0, kdtId, signature, request);
        } catch (Exception ex) {
            // 顶层 catch：所有未捕获异常（含 DB 缺列/缺表）→ HTTP 500 触发有赞重试
            log.error("[WEBHOOK/YOUZAN] unhandled exception: kdtId={} err={}", kdtId, ex.getMessage(), ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Result.error(BizCode.SYSTEM_ERROR, "处理失败：" + ex.getMessage()));
        }
    }

    /**
     * 有赞 webhook 核心处理逻辑（独立方法便于顶层 catch 包住）。
     */
    private ResponseEntity<Result<Map<String, Object>>> handleYouzanInternal(
            long t0, String kdtId, String signature, HttpServletRequest request) {

        // ---- 0. 必需参数校验 ----
        if (kdtId == null || kdtId.isEmpty()) {
            log.warn("[WEBHOOK/YOUZAN] missing kdt_id");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Result.error(BizCode.BAD_REQUEST, "缺少 kdt_id 参数"));
        }

        // ---- 1. 读原始 body（避免 Jackson 重排破坏签名） ----
        ContentCachingRequestWrapper wrapped = new ContentCachingRequestWrapper(request);
        // 触发缓存：必须先 read 才能 getContentAsByteArray
        String rawBody;
        try {
            // 读取一次以填充缓存
            byte[] buf = new byte[4096];
            try (var in = wrapped.getInputStream()) {
                while (in.read(buf) > 0) { /* drain */ }
            }
            byte[] cached = wrapped.getContentAsByteArray();
            rawBody = (cached == null || cached.length == 0) ? "" : new String(cached, StandardCharsets.UTF_8);
        } catch (Exception e) {
            log.warn("[WEBHOOK/YOUZAN] read rawBody failed: kdtId={} err={}", kdtId, e.getMessage());
            // 读取 rawBody 失败是 Bad Request（400）— 上游不应该重试同 body
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Result.error(BizCode.BAD_REQUEST, "读取请求体失败: " + e.getMessage()));
        }

        if (rawBody.isEmpty()) {
            log.warn("[WEBHOOK/YOUZAN] empty body: kdtId={}", kdtId);
            // 空 body 是 Bad Request（400）— 上游不应该重试同 body
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Result.error(BizCode.BAD_REQUEST, "请求体为空"));
        }
        if (signature == null || signature.isEmpty()) {
            log.warn("[WEBHOOK/YOUZAN] missing X-Youzan-Sign header: kdtId={}", kdtId);
            // 缺签名头是 Unauthorized（401）
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Result.error(BizCode.UNAUTHORIZED, "缺少 X-Youzan-Sign 头"));
        }

        // ---- 2. 按 kdt_id 反查平台配置 ----
        EcommercePlatform pf = platformMapper.selectOne(new LambdaQueryWrapper<EcommercePlatform>()
                .eq(EcommercePlatform::getPlatform, "YOUZAN")
                .eq(EcommercePlatform::getKdtId, kdtId)
                .last("LIMIT 1"));
        if (pf == null) {
            log.warn("[WEBHOOK/YOUZAN] platform not found: kdtId={}", kdtId);
            // 平台未配置是 Not Found（404）— 上游不应该重试同 kdt_id
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Result.error(BizCode.NOT_FOUND, "未配置该有赞店铺: kdtId=" + kdtId));
        }
        if (pf.getSignSecret() == null || pf.getSignSecret().isEmpty()) {
            log.warn("[WEBHOOK/YOUZAN] signSecret empty: kdtId={} platformId={}", kdtId, pf.getId());
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Result.error(BizCode.NOT_FOUND, "平台未配置签名密钥"));
        }

        // ---- 3. 验签 ----
        if (!YouzanEcommerceAdapter.verifyWebhookSignature(pf.getSignSecret(), rawBody, signature)) {
            log.warn("[WEBHOOK/YOUZAN] signature mismatch: kdtId={} platformId={}", kdtId, pf.getId());
            // 签名失败是 Forbidden（403）— 上游不应该重试同 rawBody（除非修 secret）
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Result.error(BizCode.FORBIDDEN, "签名校验失败"));
        }

        // ---- 4. 解析 JSON 提 msg_id / msg_type ----
        String msgId = null;
        String msgType = null;
        try {
            if (jackson != null) {
                JsonNode root = jackson.readTree(rawBody);
                // 有赞推送常见字段：id / msg_id / event / type
                JsonNode idNode = root.path("id");
                if (idNode.isMissingNode()) idNode = root.path("msg_id");
                if (!idNode.isMissingNode()) msgId = idNode.asText(null);
                JsonNode typeNode = root.path("msg_type");
                if (typeNode.isMissingNode()) typeNode = root.path("type");
                if (typeNode.isMissingNode()) typeNode = root.path("event");
                if (!typeNode.isMissingNode()) msgType = typeNode.asText(null);
            }
        } catch (Exception e) {
            log.warn("[WEBHOOK/YOUZAN] json parse failed: kdtId={} err={}", kdtId, e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Result.error(BizCode.BAD_REQUEST, "JSON 解析失败: " + e.getMessage()));
        }

        // ---- 5. msg_id 去重（幂等） ----
        if (msgId != null && !msgId.isEmpty()) {
            WebhookMsgLog existing = webhookMsgLogMapper.selectOne(new LambdaQueryWrapper<WebhookMsgLog>()
                    .eq(WebhookMsgLog::getSource, "YOUZAN")
                    .eq(WebhookMsgLog::getMsgId, msgId)
                    .gt(WebhookMsgLog::getCreatedAt, LocalDateTime.now().minusHours(24))
                    .last("LIMIT 1"));
            if (existing != null) {
                log.info("[WEBHOOK/YOUZAN] duplicate msg_id: kdtId={} msgId={} prevStatus={} (idempotent success)",
                        kdtId, msgId, existing.getStatus());
                Map<String, Object> dup = new LinkedHashMap<>();
                dup.put("duplicate", true);
                dup.put("msgId", msgId);
                dup.put("previousStatus", existing.getStatus());
                dup.put("logId", existing.getId());
                dup.put("elapsedMs", System.currentTimeMillis() - t0);
                // 幂等命中仍是成功（HTTP 200）— 上游不该重试
                return ResponseEntity.ok(Result.success("已处理（幂等）", dup));
            }
        }

        // ---- 6. upsert 订单 ----
        WebhookMsgLog logEntry = new WebhookMsgLog();
        logEntry.setSource("YOUZAN");
        logEntry.setMsgId(msgId);
        logEntry.setMsgType(msgType);
        logEntry.setSignature(signature);
        logEntry.setStatus("RECEIVED");
        logEntry.setRawPayload(rawBody);

        try {
            EcommerceOrder upserted = upsertOrder("YOUZAN", rawBody);
            if (upserted != null) {
                logEntry.setExternalOrderId(upserted.getExternalOrderId());
            }
            logEntry.setStatus("PROCESSED");
            logEntry.setProcessedAt(LocalDateTime.now());
            webhookMsgLogMapper.insert(logEntry);
            log.info("[WEBHOOK/YOUZAN] processed: kdtId={} msgId={} msgType={} orderId={} externalOrderId={} elapsed={}ms",
                    kdtId, msgId, msgType,
                    upserted == null ? null : upserted.getId(),
                    upserted == null ? null : upserted.getExternalOrderId(),
                    System.currentTimeMillis() - t0);

            Map<String, Object> data = new LinkedHashMap<>();
            data.put("duplicate", false);
            data.put("msgId", msgId);
            data.put("msgType", msgType);
            data.put("orderId", upserted == null ? null : upserted.getId());
            data.put("externalOrderId", upserted == null ? null : upserted.getExternalOrderId());
            data.put("logId", logEntry.getId());
            data.put("elapsedMs", System.currentTimeMillis() - t0);
            return ResponseEntity.ok(Result.success("ok", data));
        } catch (Exception ex) {
            logEntry.setStatus("FAILED");
            logEntry.setErrorMessage(ex.getMessage());
            logEntry.setProcessedAt(LocalDateTime.now());
            try { webhookMsgLogMapper.insert(logEntry); } catch (Exception ignore) { /* 不阻塞错误响应 */ }
            log.warn("[WEBHOOK/YOUZAN] process failed: kdtId={} msgId={} err={}", kdtId, msgId, ex.getMessage());
            // 业务处理失败（DB 缺表等）→ 上游应重试 → HTTP 500
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Result.error(BizCode.SYSTEM_ERROR, "处理失败：" + ex.getMessage()));
        }
    }

    // ============================================================
    //  upsertOrder（与 EcommerceService.upsertOrder 行为一致）
    //  因 EcommerceService.upsertOrder 是 private + 不便耦合，复制一份独立维护
    // ============================================================
    private EcommerceOrder upsertOrder(String platform, String rawBody) throws Exception {
        if (jackson == null) throw new IllegalStateException("ObjectMapper 不可用");
        @SuppressWarnings("unchecked")
        Map<String, Object> raw = jackson.readValue(rawBody, Map.class);

        EcommerceOrder o = new EcommerceOrder();
        o.setPlatform(platform);

        // ---- 主键 / 外部订单号 ----
        Object tid = raw.get("tid") != null ? raw.get("tid") : raw.get("order_id");
        String externalOrderId = tid == null ? null : String.valueOf(tid);
        o.setExternalOrderId(externalOrderId);
        o.setOrderNo(externalOrderId);

        // ---- 状态 ----
        o.setStatus(raw.get("status") == null ? null : raw.get("status").toString());

        // ---- 金额 ----
        BigDecimal total = parseDecimal(raw.get("total_amount"));
        BigDecimal shippingFee = parseDecimal(raw.get("post_fee"));
        o.setTotalAmount(total);
        o.setShippingFee(shippingFee);
        if (total != null) {
            o.setAmount(shippingFee == null ? total : total.subtract(shippingFee));
        }

        // ---- 买家信息 ----
        o.setBuyerName(firstNonNull(raw, "receiver_name", "buyer_name", "user_name"));
        o.setBuyerPhone(firstNonNull(raw, "receiver_phone", "receiver_mobile", "buyer_phone", "mobile", "phone"));

        // ---- 地址 ----
        String address = firstNonNull(raw, "receiver_address", "buyer_address", "address");
        if (address == null) {
            String state   = strOrNull(raw.get("receiver_state"));
            String city    = strOrNull(raw.get("receiver_city"));
            String district = strOrNull(raw.get("receiver_district"));
            String detail  = strOrNull(raw.get("receiver_address"));
            StringBuilder sb = new StringBuilder();
            if (state != null)    sb.append(state);
            if (city != null)     { if (sb.length() > 0) sb.append(' '); sb.append(city); }
            if (district != null) { if (sb.length() > 0) sb.append(' '); sb.append(district); }
            if (detail != null)   { if (sb.length() > 0) sb.append(' '); sb.append(detail); }
            address = sb.length() == 0 ? null : sb.toString();
        }
        o.setAddress(address);

        // ---- 物流 ----
        o.setTrackingNo(firstNonNull(raw, "express_no", "tracking_no"));
        o.setShippingStatus(firstNonNull(raw, "shipping_status", "express_company_name"));

        // ---- 商品明细 ----
        Object itemsObj = raw.get("orders") != null ? raw.get("orders") : raw.get("items");
        if (itemsObj instanceof List<?> itemsList && !itemsList.isEmpty()) {
            try {
                List<Map<String, Object>> slim = new ArrayList<>();
                for (Object it : itemsList) {
                    if (it instanceof Map<?, ?> m) {
                        Map<String, Object> one = new LinkedHashMap<>();
                        putIfPresent(one, m, "title", "title");
                        putIfPresent(one, m, "num", "num", "quantity");
                        putIfPresent(one, m, "price", "price");
                        putIfPresent(one, m, "sku_properties_name", "sku_properties_name", "sku");
                        putIfPresent(one, m, "outer_item_id", "outer_item_id");
                        putIfPresent(one, m, "item_id", "item_id");
                        slim.add(one);
                    }
                }
                o.setItemsJson(jackson.writeValueAsString(slim));
            } catch (Exception ex) {
                log.warn("[WEBHOOK/YOUZAN] items serialize failed: {}", ex.getMessage());
            }
        }

        // ---- 订单时间 ----
        Object created = raw.get("created") != null ? raw.get("created") : raw.get("update_time");
        if (created != null) {
            String ts = created.toString();
            try {
                long epoch;
                if (ts.matches("\\d{10}"))      epoch = Long.parseLong(ts) * 1000L;
                else if (ts.matches("\\d{13}")) epoch = Long.parseLong(ts);
                else                            epoch = 0;
                if (epoch > 0) {
                    o.setOrderTime(LocalDateTime.ofInstant(Instant.ofEpochMilli(epoch), ZoneId.systemDefault()));
                } else {
                    o.setOrderTime(parseDateTime(ts));
                }
            } catch (Exception ignored) {}
        }

        // ---- 原始 payload ----
        o.setRawPayload(rawBody);

        // ---- upsert ----
        EcommerceOrder existing = orderMapper.selectOne(new LambdaQueryWrapper<EcommerceOrder>()
                .eq(EcommerceOrder::getPlatform, platform)
                .eq(EcommerceOrder::getExternalOrderId, o.getExternalOrderId()));
        if (existing == null) {
            o.setId(null);
            orderMapper.insert(o);
        } else {
            o.setId(existing.getId());
            orderMapper.updateById(o);
        }
        return o;
    }

    // ---- 字段映射工具（与 EcommerceService 私有版本一致） ----

    private static String firstNonNull(Map<String, Object> raw, String... keys) {
        for (String k : keys) {
            Object v = raw.get(k);
            if (v != null) {
                String s = v.toString().trim();
                if (!s.isEmpty() && !"null".equalsIgnoreCase(s)) return s;
            }
        }
        return null;
    }

    private static BigDecimal parseDecimal(Object v) {
        if (v == null) return null;
        try { return new BigDecimal(v.toString()); } catch (Exception e) { return null; }
    }

    private static LocalDateTime parseDateTime(String s) {
        if (s == null || s.isEmpty()) return null;
        try {
            return LocalDateTime.parse(s, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        } catch (Exception ignored) {}
        try {
            return java.time.OffsetDateTime.parse(s)
                    .atZoneSameInstant(ZoneId.systemDefault()).toLocalDateTime();
        } catch (Exception ignored) {}
        return null;
    }

    @SuppressWarnings("unchecked")
    private static void putIfPresent(Map<String, Object> target, Map<?, ?> m, String targetKey, String... sourceKeys) {
        for (String sk : sourceKeys) {
            Object v = m.get(sk);
            if (v != null) {
                target.put(targetKey, v);
                return;
            }
        }
    }

    private static String strOrNull(Object v) {
        if (v == null) return null;
        String s = v.toString().trim();
        return s.isEmpty() || "null".equalsIgnoreCase(s) ? null : s;
    }
}
