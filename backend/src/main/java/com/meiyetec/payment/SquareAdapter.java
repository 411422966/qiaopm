package com.meiyetec.payment;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.meiyetec.entity.OverseasPaymentGateway;
import com.meiyetec.util.HttpUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Square Connect API Adapter
 *
 * Uses Square Connect REST API v2:
 *   Base URL: https://connect.squareup.com/v2
 *   Auth:     Bearer token (stored in gateway.secretKey)
 *
 * API endpoints:
 *   POST   /payments                     — create payment
 *   GET    /payments/{payment_id}        — retrieve payment / status
 *   POST   /payments/{payment_id}/cancel — cancel a payment
 *   POST   /refunds                     — refund a payment
 *
 * Amount convention: Square uses smallest currency unit (cents), no decimal.
 * Idempotency: all POSTs use X-Square-Idempotency-Key header.
 */
@Slf4j
@Component("squareAdapter")
public class SquareAdapter implements OverseasPaymentAdapter {

    /** Sandbox base URL */
    private static final String SANDBOX_URL = "https://connect.squareupsandbox.com/v2";
    /** Production base URL */
    private static final String PROD_URL = "https://connect.squareup.com/v2";
    private static final ObjectMapper OM = new ObjectMapper();

    @Override
    public CreatePaymentIntentResponse createPaymentIntent(OverseasPaymentGateway gateway,
                                                           CreatePaymentIntentRequest request) {
        String baseUrl = resolveBaseUrl(gateway);
        String token = gateway.getSecretKey();
        String idempotencyKey = UUID.randomUUID().toString();

        try {
            ObjectNode bodyNode = OM.createObjectNode();
            bodyNode.put("idempotency_key", idempotencyKey);

            ObjectNode paymentNode = OM.createObjectNode();
            paymentNode.put("amount_money", buildMoney(request.amount, request.currency));
            if (request.customerEmail != null && !request.customerEmail.isBlank()) {
                paymentNode.put("buyer_email_address", request.customerEmail);
            }
            if (request.description != null && !request.description.isBlank()) {
                paymentNode.put("note", request.description);
            }
            if (request.orderNo != null && !request.orderNo.isBlank()) {
                ObjectNode refNode = OM.createObjectNode();
                refNode.put("order_id", request.orderNo);
                paymentNode.set("reference_id", refNode);
            }
            // Auto-capture: true → payment immediately captured (default)
            // Set to false if you want to authorize first and capture later
            bodyNode.set("payment", paymentNode);

            String jsonBody = OM.writeValueAsString(bodyNode);

            String resp = HttpUtil.postJson(baseUrl + "/payments",
                jsonBody,
                Map.of("Authorization", "Bearer " + token,
                        "Square-Version", "2024-01-18",
                        "Content-Type", "application/json"));

            JsonNode node = OM.readTree(resp);

            if (node.has("errors") && node.path("errors").isArray()
                    && node.path("errors").size() > 0) {
                CreatePaymentIntentResponse r = new CreatePaymentIntentResponse();
                r.success = false;
                r.errorMessage = node.path("errors").get(0).path("detail").asText("square error");
                log.error("[SQUARE] createPaymentIntent error: {}", r.errorMessage);
                return r;
            }

            JsonNode payment = node.path("payment");
            CreatePaymentIntentResponse r = new CreatePaymentIntentResponse();
            r.success = true;
            r.intentId = payment.path("id").asText();
            r.status = mapSquareStatus(payment.path("status").asText());
            log.info("[SQUARE] Payment created: id={} amount={} {} status={}",
                r.intentId, request.amount, request.currency, r.status);
            return r;

        } catch (Exception e) {
            log.error("[SQUARE] createPaymentIntent exception", e);
            CreatePaymentIntentResponse r = new CreatePaymentIntentResponse();
            r.success = false;
            r.errorMessage = e.getMessage();
            return r;
        }
    }

    @Override
    public PaymentStatusResponse confirmPayment(OverseasPaymentGateway gateway, String intentId) {
        return queryPayment(gateway, intentId);
    }

    @Override
    public PaymentStatusResponse queryPayment(OverseasPaymentGateway gateway, String paymentId) {
        String baseUrl = resolveBaseUrl(gateway);
        String token = gateway.getSecretKey();

        try {
            String resp = HttpUtil.get(baseUrl + "/payments/" + paymentId,
                Map.of("Authorization", "Bearer " + token,
                        "Square-Version", "2024-01-18",
                        "Accept", "application/json"));
            JsonNode node = OM.readTree(resp);

            if (node.has("errors") && node.path("errors").isArray()
                    && node.path("errors").size() > 0) {
                PaymentStatusResponse r = new PaymentStatusResponse();
                r.success = false;
                r.errorMessage = node.path("errors").get(0).path("detail").asText("square error");
                return r;
            }

            JsonNode payment = node.path("payment");
            PaymentStatusResponse r = new PaymentStatusResponse();
            r.success = true;
            r.paymentId = payment.path("id").asText();
            r.status = mapSquareStatus(payment.path("status").asText());
            if (payment.has("amount_money")) {
                JsonNode money = payment.path("amount_money");
                String currency = money.path("currency").asText("USD").toUpperCase();
                r.amount = fromSquareAmount(money.path("amount").asLong(0), currency);
            }
            log.info("[SQUARE] Payment status: id={} status={}", r.paymentId, r.status);
            return r;

        } catch (Exception e) {
            log.error("[SQUARE] queryPayment exception: {}", paymentId, e);
            PaymentStatusResponse r = new PaymentStatusResponse();
            r.success = false;
            r.errorMessage = e.getMessage();
            return r;
        }
    }

    @Override
    public RefundResponse refund(OverseasPaymentGateway gateway, RefundRequest request) {
        String baseUrl = resolveBaseUrl(gateway);
        String token = gateway.getSecretKey();
        String idempotencyKey = UUID.randomUUID().toString();

        try {
            ObjectNode bodyNode = OM.createObjectNode();
            bodyNode.put("idempotency_key", idempotencyKey);
            bodyNode.put("payment_id", request.paymentId);
            bodyNode.put("amount_money", buildMoney(request.amount, "USD"));
            bodyNode.put("reason", request.reason != null ? request.reason : "requested_by_customer");

            String jsonBody = OM.writeValueAsString(bodyNode);

            String resp = HttpUtil.postJson(baseUrl + "/refunds",
                jsonBody,
                Map.of("Authorization", "Bearer " + token,
                        "Square-Version", "2024-01-18",
                        "Content-Type", "application/json"));
            JsonNode node = OM.readTree(resp);

            if (node.has("errors") && node.path("errors").isArray()
                    && node.path("errors").size() > 0) {
                RefundResponse r = new RefundResponse();
                r.success = false;
                r.errorMessage = node.path("errors").get(0).path("detail").asText("square error");
                log.error("[SQUARE] refund error: {}", r.errorMessage);
                return r;
            }

            JsonNode refund = node.path("refund");
            RefundResponse r = new RefundResponse();
            r.success = true;
            r.refundId = refund.path("id").asText();
            r.status = mapSquareRefundStatus(refund.path("status").asText());
            if (refund.has("amount_money")) {
                JsonNode money = refund.path("amount_money");
                r.refundAmount = fromSquareAmount(money.path("amount").asLong(0), "USD");
            }
            log.info("[SQUARE] Refund created: id={} status={}", r.refundId, r.status);
            return r;

        } catch (Exception e) {
            log.error("[SQUARE] refund exception: paymentId={}", request.paymentId, e);
            RefundResponse r = new RefundResponse();
            r.success = false;
            r.errorMessage = e.getMessage();
            return r;
        }
    }

    // ─── Webhook ────────────────────────────────────────────────────────────

    @Override
    public boolean verifyWebhookSignature(OverseasPaymentGateway gateway, String payload, String signature) {
        if (gateway.getWebhookSecret() == null || gateway.getWebhookSecret().isBlank()) {
            log.warn("[SQUARE] webhookSignature not configured, skipping verification");
            return true;
        }
        try {
            // Square webhook signature: HMAC-SHA256 of raw body using webhook signature key
            Mac mac = Mac.getInstance("HmacSHA256");
            SecretKeySpec keySpec = new SecretKeySpec(
                gateway.getWebhookSecret().getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            mac.init(keySpec);
            byte[] hash = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : hash) hex.append(String.format("%02x", b));

            boolean ok = hex.toString().equalsIgnoreCase(signature);
            if (!ok) log.warn("[SQUARE] webhook signature mismatch");
            return ok;
        } catch (Exception e) {
            log.error("[SQUARE] webhook verify exception", e);
            return false;
        }
    }

    @Override
    public WebhookEvent parseWebhookEvent(OverseasPaymentGateway gateway, String payload) {
        WebhookEvent event = new WebhookEvent();
        event.rawData = new HashMap<>();
        try {
            JsonNode node = OM.readTree(payload);
            event.eventId = node.path("notification_id").asText(UUID.randomUUID().toString());
            event.eventType = node.path("event_type").asText();
            JsonNode data = node.path("data").path("object").path("payment");
            event.paymentId = data.path("id").asText();
            event.status = mapSquareStatus(data.path("status").asText());
            @SuppressWarnings("unchecked")
            Map<String, Object> raw = OM.readValue(payload, Map.class);
            event.rawPayload = raw;
        } catch (Exception e) {
            log.error("[SQUARE] parseWebhookEvent error", e);
            event.eventType = "unknown";
        }
        return event;
    }

    // ─── HealthCheck ────────────────────────────────────────────────────────

    @Override
    public HealthStatus healthCheck(OverseasPaymentGateway gateway) {
        String baseUrl = resolveBaseUrl(gateway);
        String token = gateway.getSecretKey();
        HealthStatus status = new HealthStatus();

        try {
            // GET /v2/locations — returns 200 if token is valid, 401 otherwise
            String resp = HttpUtil.get(baseUrl + "/locations",
                Map.of("Authorization", "Bearer " + token,
                        "Square-Version", "2024-01-18",
                        "Accept", "application/json"));
            JsonNode node = OM.readTree(resp);
            if (node.has("locations") && node.path("locations").isArray()) {
                status.healthy = true;
                status.message = "Square API reachable, " +
                    node.path("locations").size() + " location(s)";
            } else {
                status.healthy = false;
                status.message = "Unexpected response";
            }
        } catch (Exception e) {
            status.healthy = false;
            status.message = "Square API unreachable: " + e.getMessage();
            log.warn("[SQUARE] healthCheck failed: {}", e.getMessage());
        }
        status.checkedAt = new Date().toString();
        return status;
    }

    @Override
    public String gatewayCode() {
        return "square";
    }

    // ─── Helpers ────────────────────────────────────────────────────────────

    private String resolveBaseUrl(OverseasPaymentGateway gateway) {
        if (gateway.getApiBaseUrl() != null && !gateway.getApiBaseUrl().isBlank()) {
            return gateway.getApiBaseUrl().replaceAll("/+$", "");
        }
        if ("production".equalsIgnoreCase(gateway.getEnvironment())) {
            return PROD_URL;
        }
        return SANDBOX_URL;
    }

    /**
     * Build Square "money" object in smallest currency unit.
     */
    private ObjectNode buildMoney(BigDecimal amount, String currency) {
        ObjectNode money = OM.createObjectNode();
        long units = toSquareAmount(amount, currency);
        money.put("amount", units);
        money.put("currency", currency != null ? currency.toUpperCase() : "USD");
        return money;
    }

    private long toSquareAmount(BigDecimal amount, String currency) {
        if (amount == null) return 0L;
        if (isZeroDecimal(currency)) {
            return amount.setScale(0, RoundingMode.DOWN).longValue();
        }
        return amount.multiply(BigDecimal.valueOf(100))
                     .setScale(0, RoundingMode.DOWN).longValue();
    }

    private BigDecimal fromSquareAmount(long units, String currency) {
        if (isZeroDecimal(currency)) {
            return BigDecimal.valueOf(units);
        }
        return BigDecimal.valueOf(units).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
    }

    private boolean isZeroDecimal(String currency) {
        if (currency == null) return false;
        return switch (currency.toUpperCase()) {
            case "BIF", "CLP", "DJF", "GNF", "JPY", "KMF", "KRW", "MGA",
                 "PYG", "RWF", "UGX", "VND", "VUV", "XAF", "XOF", "XPF" -> true;
            default -> false;
        };
    }

    /**
     * Map Square payment status to our unified status.
     * Square statuses: PENDING / COMPLETED / FAILED / REFUNDED
     */
    private String mapSquareStatus(String sqStatus) {
        if (sqStatus == null) return "UNKNOWN";
        return switch (sqStatus.toUpperCase()) {
            case "COMPLETED" -> "succeeded";
            case "PENDING"   -> "requires_payment_method";
            case "FAILED"    -> "failed";
            case "REFUNDED"  -> "refunded";
            case "APPROVED"  -> "requires_capture";
            default          -> sqStatus;
        };
    }

    private String mapSquareRefundStatus(String sqStatus) {
        if (sqStatus == null) return "UNKNOWN";
        return switch (sqStatus.toUpperCase()) {
            case "COMPLETED"  -> "succeeded";
            case "PENDING"    -> "pending";
            case "REJECTED"   -> "failed";
            default           -> sqStatus;
        };
    }
}
