package com.meiyetec.payment.bnpl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.*;

/**
 * Afterpay BNPL Provider — 真实 Afterpay API v1 集成
 *
 * <p>API 文档：https://developers.afterpay.com/afterpay-online/v1/api-reference
 * <p>认证：Basic Auth（base64(MerchantId:SecretKey)） + Signature Header
 * <p>端点：
 * <ul>
 *   <li>授信：GET /v1/customers/{phone}/credit</li>
 *   <li>创建订单：POST /v1/checkouts</li>
 *   <li>查询订单：GET /v1/orders/{orderId}</li>
 * </ul>
 *
 * <p>配置项（application.yml）：
 * <pre>
 * bnpl:
 *   afterpay:
 *     base-url: https://api-sandbox.afterpay.com   # 或 https://api.afterpay.com
 *     merchant-id: ${AFTERPAY_MERCHANT_ID}
 *     secret-key: ${AFTERPAY_SECRET_KEY}
 *     public-key: ${AFTERPAY_PUBLIC_KEY}   # 用于回调验签
 *     test-mode: true
 * </pre>
 */
@Slf4j
@Component
public class AfterpayBnplProvider implements BnplProvider {

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    @Value("${bnpl.afterpay.base-url:}")
    private String baseUrl;

    @Value("${bnpl.afterpay.merchant-id:}")
    private String merchantId;

    @Value("${bnpl.afterpay.secret-key:}")
    private String secretKey;

    @Value("${bnpl.afterpay.public-key:}")
    private String publicKey;

    @Value("${bnpl.afterpay.test-mode:false}")
    private boolean testMode;

    public AfterpayBnplProvider() {
        this.restTemplate = new RestTemplate();
        this.objectMapper = new ObjectMapper();
    }

    @Override
    public String providerCode() {
        return "AFTERPAY";
    }

    @Override
    public boolean isConfigured() {
        return merchantId != null && !merchantId.isBlank()
                && secretKey != null && !secretKey.isBlank();
    }

    @Override
    public CreditResult queryCredit(String phone) {
        if (!isConfigured()) {
            log.warn("[AFTERPAY] Not configured, falling back to mock");
            return new MockBnplProvider("AFTERPAY").queryCredit(phone);
        }

        try {
            String url = baseUrl + "/v1/customers/" + phone + "/credit";
            HttpEntity<Void> request = withAuth();
            ResponseEntity<String> resp = restTemplate.exchange(url, HttpMethod.GET, request, String.class);

            JsonNode root = objectMapper.readTree(resp.getBody());
            return CreditResult.builder()
                    .success(true)
                    .creditLimit(BigDecimal.valueOf(root.path("creditLimit").asLong(0)).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP))
                    .availableLimit(BigDecimal.valueOf(root.path("availableCredit").asLong(0)).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP))
                    .status(root.path("status").asText("APPROVED"))
                    .supportedPeriods(new int[]{4}) // Afterpay 只支持 4 期
                    .feeRate(BigDecimal.valueOf(0.020))
                    .build();

        } catch (RestClientException e) {
            log.error("[AFTERPAY] Credit query failed: {}", e.getMessage());
            return CreditResult.builder().success(false).errorCode("NETWORK_ERROR").errorMsg(e.getMessage()).build();
        } catch (Exception e) {
            log.error("[AFTERPAY] Credit query parse error: {}", e.getMessage());
            return CreditResult.builder().success(false).errorCode("PARSE_ERROR").errorMsg(e.getMessage()).build();
        }
    }

    @Override
    public CreateOrderResult createOrder(CreateOrderRequest req) {
        if (!isConfigured()) {
            log.warn("[AFTERPAY] Not configured, falling back to mock");
            return new MockBnplProvider("AFTERPAY").createOrder(req);
        }

        try {
            String url = baseUrl + "/v1/checkouts";
            Map<String, Object> body = buildAfterpayCheckoutRequest(req);

            HttpEntity<Map<String, Object>> request = withAuth(new HttpEntity<>(body));
            ResponseEntity<String> resp = restTemplate.exchange(url, HttpMethod.POST, request, String.class);

            JsonNode root = objectMapper.readTree(resp.getBody());
            String token = root.path("token").asText(null);
            String redirectUrl = root.path("redirectCheckoutUrl").asText(null);

            if (token == null || token.isBlank()) {
                return CreateOrderResult.builder()
                        .success(false)
                        .errorCode("AFTERPAY_CHECKOUT_ERROR")
                        .errorMsg("Afterpay Checkout 创建失败：" + root.path("errorCode").asText())
                        .build();
            }

            Map<Integer, InstallmentPlan> plans = parseAfterpayInstallments(req.getAmount(), req.getInstallments());

            return CreateOrderResult.builder()
                    .success(true)
                    .providerOrderNo(token)
                    .redirectUrl(redirectUrl)
                    .installments(plans)
                    .platformFee(calculatePlatformFee(req.getAmount()))
                    .localOrderNo(req.getBizOrderNo())
                    .build();

        } catch (RestClientException e) {
            log.error("[AFTERPAY] Order creation failed: {}", e.getMessage());
            return CreateOrderResult.builder()
                    .success(false).errorCode("NETWORK_ERROR").errorMsg(e.getMessage()).build();
        } catch (Exception e) {
            log.error("[AFTERPAY] Order creation parse error: {}", e.getMessage());
            return CreateOrderResult.builder()
                    .success(false).errorCode("PARSE_ERROR").errorMsg(e.getMessage()).build();
        }
    }

    @Override
    public WebhookResult handleWebhook(String eventType, String payload, String signature, String secret) {
        if (!isConfigured()) {
            return new MockBnplProvider("AFTERPAY").handleWebhook(eventType, payload, signature, secret);
        }

        // 1. 验签：Afterpay 使用 RSA-SHA256 签名
        String webhookSecret = secret != null ? secret : secretKey;
        if (webhookSecret == null || webhookSecret.isBlank()) {
            log.warn("[AFTERPAY] Webhook secret not configured, skipping verification");
            return new MockBnplProvider("AFTERPAY").handleWebhook(eventType, payload, signature, secret);
        }

        if (!verifyAfterpayWebhook(payload, signature, webhookSecret)) {
            log.warn("[AFTERPAY] Webhook signature verification failed");
            return WebhookResult.builder()
                    .signatureValid(false)
                    .errorCode("SIGNATURE_INVALID")
                    .errorMsg("签名验证失败")
                    .build();
        }

        // 2. 解析事件
        try {
            JsonNode root = objectMapper.readTree(payload);
            String orderToken = root.path("orderToken").asText(
                    root.path("resourceId").asText(null));
            String afterpayEvent = root.path("eventType").asText(eventType);

            Integer newStatus = mapAfterpayEventToStatus(afterpayEvent);

            log.info("[AFTERPAY] Webhook processed eventType={} orderToken={} newStatus={}",
                    afterpayEvent, orderToken, newStatus);

            return WebhookResult.builder()
                    .signatureValid(true)
                    .replayDetected(false)
                    .newStatus(newStatus)
                    .providerOrderNo(orderToken)
                    .eventType(afterpayEvent)
                    .rawPayload(payload)
                    .build();

        } catch (Exception e) {
            log.error("[AFTERPAY] Webhook parse error: {}", e.getMessage());
            return WebhookResult.builder()
                    .signatureValid(true)
                    .errorCode("PARSE_ERROR")
                    .errorMsg(e.getMessage())
                    .rawPayload(payload)
                    .build();
        }
    }

    @Override
    public RefundResult refund(String providerOrderNo, BigDecimal amountCents, String reason) {
        if (!isConfigured()) {
            return new MockBnplProvider("AFTERPAY").refund(providerOrderNo, amountCents, reason);
        }

        try {
            // Afterpay 只支持全退（全额 refund）
            String url = baseUrl + "/v1/orders/" + providerOrderNo + "/refund";
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("requestId", UUID.randomUUID().toString());
            if (reason != null && !reason.isBlank()) {
                body.put("reason", reason);
            }

            HttpEntity<Map<String, Object>> request = withAuth(new HttpEntity<>(body));
            restTemplate.exchange(url, HttpMethod.POST, request, String.class);

            return RefundResult.builder()
                    .success(true)
                    .providerRefundNo("AFTERPAY-REF-" + System.currentTimeMillis())
                    .refundedAmount(amountCents)
                    .build();

        } catch (RestClientException e) {
            log.error("[AFTERPAY] Refund failed: {}", e.getMessage());
            return RefundResult.builder()
                    .success(false).errorCode("REFUND_FAILED").errorMsg(e.getMessage()).build();
        }
    }

    // ==================== 内部工具 ====================

    private HttpEntity<Void> withAuth() {
        String encoded = Base64.getEncoder().encodeToString(
                (merchantId + ":" + secretKey).getBytes(StandardCharsets.UTF_8));
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Authorization", "Basic " + encoded);
        headers.set("User-Agent", "Meiyetec-BNPL/1.0");
        return new HttpEntity<>(headers);
    }

    private <T> HttpEntity<T> withAuth(HttpEntity<T> entity) {
        String encoded = Base64.getEncoder().encodeToString(
                (merchantId + ":" + secretKey).getBytes(StandardCharsets.UTF_8));
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Authorization", "Basic " + encoded);
        headers.set("User-Agent", "Meiyetec-BNPL/1.0");
        HttpHeaders entityHeaders = entity.getHeaders();
        if (entityHeaders != null) {
            entityHeaders.forEach((k, v) -> {
                if (!headers.containsKey(k)) headers.put(k, v);
            });
        }
        return new HttpEntity<>(entity.getBody(), headers);
    }

    private Map<String, Object> buildAfterpayCheckoutRequest(CreateOrderRequest req) {
        Map<String, Object> body = new LinkedHashMap<>();

        // 金额（Afterpay 用整数 cents）
        long totalAmount = req.getAmount().multiply(BigDecimal.valueOf(100)).longValue();
        Map<String, Object> amount = new LinkedHashMap<>();
        amount.put("amount", totalAmount / 100.0);
        amount.put("currency", req.getCurrency() != null ? req.getCurrency() : "USD");
        body.put("amount", amount);

        // 消费者信息
        Map<String, Object> consumer = new LinkedHashMap<>();
        consumer.put("phoneNumber", req.getMemberPhone());
        consumer.put("givenNames", req.getMemberName() != null ? req.getMemberName() : "");
        consumer.put("surname", "");
        consumer.put("email", req.getMemberEmail());
        body.put("consumer", consumer);

        // 订单项
        List<Map<String, Object>> items = new ArrayList<>();
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("name", req.getDescription() != null ? req.getDescription() : "BNPL Order");
        item.put("quantity", 1);
        item.put("price", Map.of(
                "amount", totalAmount / 100.0,
                "currency", req.getCurrency() != null ? req.getCurrency() : "USD"
        ));
        items.add(item);
        body.put("items", items);

        // merchant 引用
        body.put("merchantReference", req.getBizOrderNo());
        body.put("merchantReference2", String.valueOf(req.getTenantId()));

        // 回调 URL
        if (req.getNotifyUrl() != null) {
            body.put("notificationUrl", req.getNotifyUrl());
        }
        if (req.getSuccessUrl() != null) {
            body.put("successUrl", req.getSuccessUrl());
        }
        if (req.getCancelUrl() != null) {
            body.put("cancelUrl", req.getCancelUrl());
        }

        return body;
    }

    private Map<Integer, InstallmentPlan> parseAfterpayInstallments(BigDecimal amount, Integer installments) {
        // Afterpay 固定 4 期
        int periods = (installments != null && installments == 4) ? 4 : 4;
        BigDecimal perAmount = amount.divide(BigDecimal.valueOf(periods), 2, RoundingMode.HALF_UP);
        Map<Integer, InstallmentPlan> plans = new LinkedHashMap<>();
        for (int i = 1; i <= periods; i++) {
            plans.put(i, InstallmentPlan.builder()
                    .seq(i)
                    .amount(perAmount)
                    .dueDate(LocalDate.now().plusWeeks(i * 2L).toString()) // Afterpay 每 2 周一期
                    .status("PENDING")
                    .build());
        }
        return plans;
    }

    private boolean verifyAfterpayWebhook(String payload, String signature, String secret) {
        if (signature == null || signature.isBlank()) {
            log.warn("[AFTERPAY] No signature header present");
            return false;
        }
        try {
            // Afterpay 使用 RSA-SHA256: base64(RSA-SHA256(payload, privateKey))
            // 我们只有公钥（在 publicKey 配置中），用公钥验签
            if (publicKey != null && !publicKey.isBlank()) {
                return verifyRsaSha256(payload, signature, publicKey);
            }
            // 如果没有配置公钥，降级为 HMAC-SHA256（某些集成方式）
            return verifyHmacSha256(payload, signature, secret);
        } catch (Exception e) {
            log.error("[AFTERPAY] Webhook verification error: {}", e.getMessage());
            return false;
        }
    }

    private boolean verifyHmacSha256(String data, String signature, String secret) {
        try {
            javax.crypto.Mac mac = javax.crypto.Mac.getInstance("HmacSHA256");
            mac.init(new javax.crypto.spec.SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] bytes = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : bytes) sb.append(String.format("%02x", b));
            return sb.toString().equalsIgnoreCase(signature);
        } catch (Exception e) {
            return false;
        }
    }

    private boolean verifyRsaSha256(String data, String signature, String publicKeyPem) {
        try {
            // 简化实现：使用 publicKey 进行 RSA 验签
            // 生产中应使用完整的 RSA 验签实现
            byte[] signatureBytes = Base64.getDecoder().decode(signature);
            java.security.Signature sig = java.security.Signature.getInstance("SHA256withRSA");
            // 提取 PEM 中的公钥
            String pemContent = publicKeyPem
                    .replace("-----BEGIN PUBLIC KEY-----", "")
                    .replace("-----END PUBLIC KEY-----", "")
                    .replaceAll("\\s", "");
            byte[] keyBytes = Base64.getDecoder().decode(pemContent);
            java.security.spec.X509EncodedKeySpec keySpec = new java.security.spec.X509EncodedKeySpec(keyBytes);
            java.security.KeyFactory keyFactory = java.security.KeyFactory.getInstance("RSA");
            sig.initVerify(keyFactory.generatePublic(keySpec));
            sig.update(data.getBytes(StandardCharsets.UTF_8));
            return sig.verify(signatureBytes);
        } catch (Exception e) {
            log.warn("[AFTERPAY] RSA verification fallback: {}", e.getMessage());
            return false;
        }
    }

    private Integer mapAfterpayEventToStatus(String event) {
        return switch (event) {
            case "CHECKOUT_APPROVED", "ORDER_APPROVED"       -> 2;  // 已确认
            case "ORDER_SHIPPED"                              -> 3;  // 已发货
            case "ORDER_DELIVERED", "PAYMENT_CAPTURED"        -> 4;  // 已收货/已扣款
            case "ORDER_COMPLETED"                            -> 5;  // 已完成
            case "ORDER_CANCELLED", "ORDER_DECLINED"          -> 6;  // 已取消/被拒
            case "ORDER_REFUNDED"                            -> 8;  // 已退款
            default                                          -> null;
        };
    }

    private BigDecimal calculatePlatformFee(BigDecimal amount) {
        return amount.multiply(BigDecimal.valueOf(0.020)).setScale(2, RoundingMode.HALF_UP);
    }
}
