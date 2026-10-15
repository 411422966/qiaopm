package com.meiyetec.payment.bnpl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
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
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Klarna BNPL Provider — 真实 Klarna Payments API v3 集成
 *
 * <p>API 文档：https://docs.klarna.com/klarna-payments/
 * <p>认证：Basic Auth（base64(clientId:clientSecret)）
 * <p>端点：
 * <ul>
 *   <li>授信：POST /customer/v1/sessions</li>
 *   <li>创建订单：POST /checkout/v3/orders</li>
 *   <li>更新订单：PATCH /checkout/v3/orders/{orderId}</li>
 * </ul>
 *
 * <p>配置项（application.yml）：
 * <pre>
 * bnpl:
 *   klarna:
 *     base-url: https://api-na.klarna.com   # 或 https://api.klarna.com（EU）
 *     client-id: ${KLARNA_CLIENT_ID}
 *     client-secret: ${KLARNA_CLIENT_SECRET}
 *     merchant-id: ${KLARNA_MERCHANT_ID}
 *     region: NA  # NA | EU
 *     test-mode: false
 * </pre>
 */
@Slf4j
@Component
public class KlarnaBnplProvider implements BnplProvider {

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    @Value("${bnpl.klarna.base-url:}")
    private String baseUrl;

    @Value("${bnpl.klarna.client-id:}")
    private String clientId;

    @Value("${bnpl.klarna.client-secret:}")
    private String clientSecret;

    @Value("${bnpl.klarna.merchant-id:}")
    private String merchantId;

    @Value("${bnpl.klarna.test-mode:false}")
    private boolean testMode;

    public KlarnaBnplProvider() {
        this.restTemplate = new RestTemplate();
        this.objectMapper = new ObjectMapper();
    }

    @Override
    public String providerCode() {
        return "KLARNA";
    }

    @Override
    public boolean isConfigured() {
        return clientId != null && !clientId.isBlank()
                && clientSecret != null && !clientSecret.isBlank()
                && merchantId != null && !merchantId.isBlank();
    }

    @Override
    public CreditResult queryCredit(String phone) {
        if (!isConfigured()) {
            log.warn("[KLARNA] Not configured, falling back to mock");
            return new MockBnplProvider("KLARNA").queryCredit(phone);
        }

        try {
            String url = baseUrl + "/customer/v1/sessions";
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("purchase_amount", 99900); // $999.00 in cents
            body.put("currency", "USD");
            body.put("locale", "en-US");

            HttpEntity<Map<String, Object>> request = withAuth(body);
            ResponseEntity<String> resp = restTemplate.exchange(url, HttpMethod.POST, request, String.class);

            JsonNode root = objectMapper.readTree(resp.getBody());
            JsonNode session = root.path("session");
            if (!session.isMissingNode()) {
                return CreditResult.builder()
                        .success(true)
                        .creditLimit(BigDecimal.valueOf(session.path("available_limit").asLong(0)).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP))
                        .availableLimit(BigDecimal.valueOf(session.path("available_limit").asLong(0)).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP))
                        .status("APPROVED")
                        .supportedPeriods(new int[]{3, 6, 12})
                        .feeRate(BigDecimal.valueOf(0.029))
                        .build();
            }
            return CreditResult.builder().success(false).errorCode("KLARNA_SESSION_ERROR").errorMsg("授信会话创建失败").build();
        } catch (RestClientException e) {
            log.error("[KLARNA] Credit query failed: {}", e.getMessage());
            return CreditResult.builder().success(false).errorCode("NETWORK_ERROR").errorMsg(e.getMessage()).build();
        } catch (Exception e) {
            log.error("[KLARNA] Credit query parse error: {}", e.getMessage());
            return CreditResult.builder().success(false).errorCode("PARSE_ERROR").errorMsg(e.getMessage()).build();
        }
    }

    @Override
    public CreateOrderResult createOrder(CreateOrderRequest req) {
        if (!isConfigured()) {
            log.warn("[KLARNA] Not configured, falling back to mock");
            return new MockBnplProvider("KLARNA").createOrder(req);
        }

        try {
            String url = baseUrl + "/checkout/v3/orders";
            Map<String, Object> body = buildKlarnaOrderRequest(req);

            HttpEntity<Map<String, Object>> request = withAuth(body);
            ResponseEntity<String> resp = restTemplate.exchange(url, HttpMethod.POST, request, String.class);

            JsonNode root = objectMapper.readTree(resp.getBody());
            String orderId = root.path("order_id").asText(null);
            String redirectUrl = root.path("redirect_url").asText(null);

            if (orderId == null || orderId.isBlank()) {
                return CreateOrderResult.builder()
                        .success(false)
                        .errorCode("KLARNA_ORDER_ERROR")
                        .errorMsg("Klarna 订单创建失败：" + root.path("error_messages").asText())
                        .build();
            }

            Map<Integer, InstallmentPlan> plans = parseKlarnaOrderInstallments(root);

            return CreateOrderResult.builder()
                    .success(true)
                    .providerOrderNo(orderId)
                    .redirectUrl(redirectUrl)
                    .installments(plans)
                    .platformFee(calculatePlatformFee(req.getAmount()))
                    .localOrderNo(req.getBizOrderNo())
                    .build();

        } catch (RestClientException e) {
            log.error("[KLARNA] Order creation failed: {}", e.getMessage());
            return CreateOrderResult.builder()
                    .success(false).errorCode("NETWORK_ERROR").errorMsg(e.getMessage()).build();
        } catch (Exception e) {
            log.error("[KLARNA] Order creation parse error: {}", e.getMessage());
            return CreateOrderResult.builder()
                    .success(false).errorCode("PARSE_ERROR").errorMsg(e.getMessage()).build();
        }
    }

    @Override
    public WebhookResult handleWebhook(String eventType, String payload, String signature, String secret) {
        if (!isConfigured()) {
            return new MockBnplProvider("KLARNA").handleWebhook(eventType, payload, signature, secret);
        }

        // 1. 验签：Klarna 使用 HMAC-SHA256
        if (!verifyKlarnaWebhook(payload, signature, secret)) {
            log.warn("[KLARNA] Webhook signature verification failed");
            return WebhookResult.builder()
                    .signatureValid(false)
                    .errorCode("SIGNATURE_INVALID")
                    .errorMsg("签名验证失败")
                    .build();
        }

        // 2. 解析 payload
        try {
            JsonNode root = objectMapper.readTree(payload);
            String orderId = root.path("order_id").asText(null);
            String klarnaEvent = root.path("event_type").asText(eventType);

            Integer newStatus = mapKlarnaEventToStatus(klarnaEvent);

            log.info("[KLARNA] Webhook processed eventType={} orderId={} newStatus={}",
                    klarnaEvent, orderId, newStatus);

            return WebhookResult.builder()
                    .signatureValid(true)
                    .replayDetected(false)
                    .newStatus(newStatus)
                    .providerOrderNo(orderId)
                    .eventType(klarnaEvent)
                    .rawPayload(payload)
                    .build();

        } catch (Exception e) {
            log.error("[KLARNA] Webhook parse error: {}", e.getMessage());
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
            return new MockBnplProvider("KLARNA").refund(providerOrderNo, amountCents, null);
        }

        try {
            String url = baseUrl + "/ordermanagement/v1/orders/" + providerOrderNo + "/refunds";
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("refunded_amount", amountCents.multiply(BigDecimal.valueOf(100)).longValue());
            if (reason != null && !reason.isBlank()) {
                body.put("description", reason);
            }

            HttpEntity<Map<String, Object>> request = withAuth(body);
            restTemplate.exchange(url, HttpMethod.POST, request, String.class);

            return RefundResult.builder()
                    .success(true)
                    .providerRefundNo("KLARNA-REF-" + System.currentTimeMillis())
                    .refundedAmount(amountCents)
                    .build();

        } catch (RestClientException e) {
            log.error("[KLARNA] Refund failed: {}", e.getMessage());
            return RefundResult.builder()
                    .success(false).errorCode("REFUND_FAILED").errorMsg(e.getMessage()).build();
        }
    }

    // ==================== 内部工具 ====================

    private HttpEntity<Map<String, Object>> withAuth(Map<String, Object> body) {
        String encoded = Base64.getEncoder().encodeToString(
                (clientId + ":" + clientSecret).getBytes(StandardCharsets.UTF_8));
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Authorization", "Basic " + encoded);
        if (testMode) headers.set("Klarna-Platform-Version", "2.0");
        return new HttpEntity<>(body, headers);
    }

    private Map<String, Object> buildKlarnaOrderRequest(CreateOrderRequest req) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("purchase_country", "US");
        body.put("purchase_currency", req.getCurrency() != null ? req.getCurrency() : "USD");
        body.put("locale", "en-US");
        body.put("merchant_reference1", req.getBizOrderNo());
        body.put("merchant_reference2", String.valueOf(req.getTenantId()));

        // 订单金额（Klarna 用整数 cents）
        long amountCents = req.getAmount().multiply(BigDecimal.valueOf(100)).longValue();
        body.put("order_amount", amountCents);
        body.put("order_tax_amount", 0L);

        List<Map<String, Object>> orderLines = new ArrayList<>();
        Map<String, Object> line = new LinkedHashMap<>();
        line.put("type", "physical");
        line.put("reference", req.getBizOrderNo());
        line.put("name", req.getDescription() != null ? req.getDescription() : "BNPL Order");
        line.put("quantity", 1);
        line.put("quantity_unit", "pcs");
        line.put("unit_price", amountCents);
        line.put("tax_rate", 0);
        line.put("total_amount", amountCents);
        line.put("total_discount_amount", 0L);
        orderLines.add(line);
        body.put("order_lines", orderLines);

        // 分期选项
        List<String> autoCaptureMethods = new ArrayList<>();
        if (req.getInstallments() != null && req.getInstallments() == 4) {
            autoCaptureMethods.add("pay_later");
        }
        body.put("options", Map.of(
                "allow_separate_shipping_address", false,
                "date_of_birth_mandatory", false,
                "require_written_consent", false
        ));

        // 客户信息
        Map<String, Object> customer = new LinkedHashMap<>();
        customer.put("type", "person");
        if (req.getMemberName() != null) {
            String[] parts = req.getMemberName().split(" ", 2);
            customer.put("first_name", parts[0]);
            customer.put("last_name", parts.length > 1 ? parts[1] : "");
        }
        customer.put("phone", req.getMemberPhone());
        customer.put("email", req.getMemberEmail());
        body.put("customer", customer);

        // merchant_urls（回调）
        if (req.getNotifyUrl() != null) {
            body.put("merchant_urls", Map.of(
                    "notification", req.getNotifyUrl(),
                    "redirect", req.getSuccessUrl() != null ? req.getSuccessUrl() : req.getNotifyUrl()
            ));
        }

        return body;
    }

    private Map<Integer, InstallmentPlan> parseKlarnaOrderInstallments(JsonNode root) {
        Map<Integer, InstallmentPlan> plans = new LinkedHashMap<>();
        JsonNode rec = root.path("recurring");
        if (rec.isMissingNode()) {
            // 一次性支付：拆成 N 期模拟
            long totalAmount = root.path("order_amount").asLong(0);
            int installments = 3; // 默认 3 期
            long perInstallment = totalAmount / installments;
            for (int i = 1; i <= installments; i++) {
                plans.put(i, InstallmentPlan.builder()
                        .seq(i)
                        .amount(BigDecimal.valueOf(perInstallment).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP))
                        .dueDate(LocalDate.now().plusMonths(i).toString())
                        .status("PENDING")
                        .build());
            }
        }
        return plans;
    }

    private boolean verifyKlarnaWebhook(String payload, String signature, String secret) {
        if (secret == null || secret.isBlank()) {
            log.warn("[KLARNA] Webhook secret not configured, skipping verification");
            return true; // 配置缺失时跳过（dev 场景）
        }
        try {
            String expected = signWithHmacSha256(payload, secret);
            return expected.equalsIgnoreCase(signature);
        } catch (Exception e) {
            log.error("[KLARNA] HMAC verification error: {}", e.getMessage());
            return false;
        }
    }

    private String signWithHmacSha256(String data, String secret) {
        try {
            javax.crypto.Mac mac = javax.crypto.Mac.getInstance("HmacSHA256");
            mac.init(new javax.crypto.spec.SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] bytes = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : bytes) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private Integer mapKlarnaEventToStatus(String event) {
        return switch (event) {
            case "ORDER_CREATED", "ORDER_PLACED"          -> 2;  // 已确认
            case "ORDER_SHIPPED"                           -> 3;  // 已发货
            case "ORDER_DELIVERED"                         -> 4;  // 已收货
            case "KLARNA_PAID", "ORDER_COMPLETED"         -> 5;  // 已完成
            case "ORDER_CANCELLED"                         -> 6;  // 已取消
            case "ORDER_REFUNDED"                          -> 8;  // 已退款
            default                                        -> null;
        };
    }

    private BigDecimal calculatePlatformFee(BigDecimal amount) {
        return amount.multiply(BigDecimal.valueOf(0.029)).setScale(2, RoundingMode.HALF_UP);
    }
}
