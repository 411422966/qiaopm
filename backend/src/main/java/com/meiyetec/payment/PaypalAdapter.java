package com.meiyetec.payment;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.meiyetec.entity.OverseasPaymentGateway;
import com.meiyetec.util.HttpUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Date;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * PayPal REST API Adapter (v2 Checkout / Orders API)
 *
 * Uses PayPal REST API v2:
 *   Sandbox: https://api-m.sandbox.paypal.com
 *   Prod:    https://api-m.paypal.com
 *   Auth:    OAuth2 Bearer token (client_credentials grant)
 *
 * Flow:
 *   1. createPaymentIntent  → POST /v2/checkout/orders  → approval_url
 *   2. queryPayment         → GET  /v2/checkout/orders/{id}  → status
 *   3. confirmPayment       → POST /v2/checkout/orders/{id}/capture → capture_id
 *   4. refund                → POST /v2/payments/captures/{id}/refund
 *
 * Amount convention: PayPal uses decimal string (e.g. "10.00"), currency uppercase.
 *
 * Token caching: access token is cached per-gateway-key with 1-hour TTL to avoid
 * repeated OAuth2 calls on every request.
 */
@Slf4j
@Component("paypalAdapter")
public class PaypalAdapter implements OverseasPaymentAdapter {

    private static final ObjectMapper OM = new ObjectMapper();

    /** Cache: key = secretKey hash → {token, expireEpochMs} */
    private static final Map<String, TokenCacheEntry> TOKEN_CACHE = new ConcurrentHashMap<>();

    @Override
    public CreatePaymentIntentResponse createPaymentIntent(OverseasPaymentGateway gateway,
                                                           CreatePaymentIntentRequest request) {
        try {
            String token = acquireToken(gateway);
            String baseUrl = resolveBaseUrl(gateway);

            ObjectNode body = buildCreateOrderBody(request);
            String jsonBody = OM.writeValueAsString(body);

            String resp = HttpUtil.postJson(
                baseUrl + "/v2/checkout/orders",
                jsonBody,
                Map.of("Authorization", "Bearer " + token,
                        "Content-Type", "application/json",
                        "PayPal-Request-Id", request.orderNo != null ? request.orderNo
                            : java.util.UUID.randomUUID().toString()));

            JsonNode node = OM.readTree(resp);

            if (node.has("error") || (node.has("name") && !node.path("name").asText().isBlank())) {
                CreatePaymentIntentResponse r = new CreatePaymentIntentResponse();
                r.success = false;
                r.errorMessage = node.path("error_description").asText(
                    node.path("message").asText("paypal error"));
                log.error("[PAYPAL] createOrder error: {}", r.errorMessage);
                return r;
            }

            String orderId = node.path("id").asText();
            String approvalUrl = null;
            for (JsonNode link : node.path("links")) {
                if ("approve".equals(link.path("rel").asText())) {
                    approvalUrl = link.path("href").asText();
                    break;
                }
            }
            String status = node.path("status").asText();

            CreatePaymentIntentResponse r = new CreatePaymentIntentResponse();
            r.success = true;
            r.intentId = orderId;
            r.clientSecret = orderId; // PayPal doesn't have a client_secret concept
            r.approvalUrl = approvalUrl;
            r.status = mapPaypalOrderStatus(status);
            log.info("[PAYPAL] Order created: id={} amount={} {} status={} approvalUrl={}",
                orderId, request.amount, request.currency, r.status, approvalUrl);
            return r;

        } catch (Exception e) {
            log.error("[PAYPAL] createPaymentIntent exception", e);
            CreatePaymentIntentResponse r = new CreatePaymentIntentResponse();
            r.success = false;
            r.errorMessage = e.getMessage();
            return r;
        }
    }

    @Override
    public PaymentStatusResponse confirmPayment(OverseasPaymentGateway gateway, String orderId) {
        String baseUrl = resolveBaseUrl(gateway);
        try {
            String token = acquireToken(gateway);

            String resp = HttpUtil.postJson(
                baseUrl + "/v2/checkout/orders/" + orderId + "/capture",
                "{}",  // empty body
                Map.of("Authorization", "Bearer " + token,
                        "Content-Type", "application/json",
                        "PayPal-Request-Id", orderId + "-capture"));

            JsonNode node = OM.readTree(resp);

            if (node.has("error") || node.has("name") && !node.path("name").asText().isBlank()) {
                PaymentStatusResponse r = new PaymentStatusResponse();
                r.success = false;
                r.errorMessage = node.path("error_description").asText(
                    node.path("message").asText("paypal capture error"));
                log.error("[PAYPAL] capture error: {}", r.errorMessage);
                return r;
            }

            String status = node.path("status").asText();
            String captureId = null;
            BigDecimal capturedAmount = null;
            for (JsonNode purchase : node.path("purchase_units")) {
                for (JsonNode capture : purchase.path("payments").path("captures")) {
                    if (captureId == null) {
                        captureId = capture.path("id").asText();
                        JsonNode money = capture.path("amount");
                        capturedAmount = new BigDecimal(money.path("value").asText("0"));
                    }
                }
            }

            PaymentStatusResponse r = new PaymentStatusResponse();
            r.success = true;
            r.paymentId = orderId;
            r.status = mapPaypalOrderStatus(status);
            r.amount = capturedAmount;
            log.info("[PAYPAL] Order captured: orderId={} captureId={} status={}",
                orderId, captureId, r.status);
            return r;

        } catch (Exception e) {
            log.error("[PAYPAL] confirmPayment exception: {}", orderId, e);
            PaymentStatusResponse r = new PaymentStatusResponse();
            r.success = false;
            r.errorMessage = e.getMessage();
            return r;
        }
    }

    @Override
    public PaymentStatusResponse queryPayment(OverseasPaymentGateway gateway, String orderId) {
        String baseUrl = resolveBaseUrl(gateway);
        try {
            String token = acquireToken(gateway);

            String resp = HttpUtil.get(
                baseUrl + "/v2/checkout/orders/" + orderId,
                Map.of("Authorization", "Bearer " + token,
                        "Accept", "application/json"));
            JsonNode node = OM.readTree(resp);

            if (node.has("error") || node.has("name") && !node.path("name").asText().isBlank()) {
                PaymentStatusResponse r = new PaymentStatusResponse();
                r.success = false;
                r.errorMessage = node.path("error_description").asText("paypal error");
                return r;
            }

            String status = node.path("status").asText();
            BigDecimal amount = null;
            for (JsonNode pu : node.path("purchase_units")) {
                if (amount == null) {
                    JsonNode amountNode = pu.path("amount");
                    amount = new BigDecimal(amountNode.path("value").asText("0"));
                }
            }

            PaymentStatusResponse r = new PaymentStatusResponse();
            r.success = true;
            r.paymentId = orderId;
            r.status = mapPaypalOrderStatus(status);
            r.amount = amount;
            log.info("[PAYPAL] Order status: id={} status={}", orderId, r.status);
            return r;

        } catch (Exception e) {
            log.error("[PAYPAL] queryPayment exception: {}", orderId, e);
            PaymentStatusResponse r = new PaymentStatusResponse();
            r.success = false;
            r.errorMessage = e.getMessage();
            return r;
        }
    }

    @Override
    public RefundResponse refund(OverseasPaymentGateway gateway, RefundRequest request) {
        String baseUrl = resolveBaseUrl(gateway);
        try {
            String token = acquireToken(gateway);
            // PayPal refund requires a capture_id, not an order_id.
            // In our flow, confirmPayment returns the capture_id in status or we
            // retrieve it from the order. Here we assume paymentId == capture_id
            // (set by confirmPayment when it stored the capture_id in payment record).
            // If the caller passes order_id instead, try to get capture_id first.
            String captureId = resolveCaptureId(gateway, token, baseUrl, request.paymentId);

            ObjectNode body = OM.createObjectNode();
            body.putPOJO("amount", Map.of(
                "value", request.amount != null ? request.amount.setScale(2).toPlainString() : "0.00",
                "currency_code", "USD"));

            String resp = HttpUtil.postJson(
                baseUrl + "/v2/payments/captures/" + captureId + "/refund",
                OM.writeValueAsString(body),
                Map.of("Authorization", "Bearer " + token,
                        "Content-Type", "application/json",
                        "PayPal-Request-Id", captureId + "-refund"));

            JsonNode node = OM.readTree(resp);

            if (node.has("error") || node.has("name") && !node.path("name").asText().isBlank()) {
                RefundResponse r = new RefundResponse();
                r.success = false;
                r.errorMessage = node.path("error_description").asText(
                    node.path("message").asText("paypal refund error"));
                log.error("[PAYPAL] refund error: {}", r.errorMessage);
                return r;
            }

            RefundResponse r = new RefundResponse();
            r.success = true;
            r.refundId = node.path("id").asText();
            r.status = mapPaypalRefundStatus(node.path("status").asText());
            JsonNode money = node.path("amount");
            r.refundAmount = new BigDecimal(money.path("value").asText("0"));
            log.info("[PAYPAL] Refund created: id={} status={}", r.refundId, r.status);
            return r;

        } catch (Exception e) {
            log.error("[PAYPAL] refund exception: paymentId={}", request.paymentId, e);
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
            log.warn("[PAYPAL] webhookSecret not configured, skipping verification");
            return true;
        }
        try {
            // PayPal webhook verification via verify-webhook-signature API:
            // POST /v1/notifications/verify-webhook-signature
            String baseUrl = resolveBaseUrl(gateway);
            String token = acquireToken(gateway);

            ObjectNode body = OM.createObjectNode();
            body.put("auth_algo", "SHA256withRSA");
            body.put("cert_url", ""); // passed via headers in production; here use empty for simple mode
            body.put("transmission_id", "");
            body.put("transmission_sig", signature);
            body.put("transmission_time", new Date().toString());
            body.putPOJO("webhook_id", gateway.getWebhookSecret());
            body.putPOJO("webhook_event", OM.readTree(payload));

            String resp = HttpUtil.postJson(
                baseUrl + "/v1/notifications/verify-webhook-signature",
                OM.writeValueAsString(body),
                Map.of("Authorization", "Bearer " + token,
                        "Content-Type", "application/json"));

            JsonNode node = OM.readTree(resp);
            boolean ok = "SUCCESS".equalsIgnoreCase(node.path("verification_status").asText());
            if (!ok) log.warn("[PAYPAL] webhook verify failed: {}", resp);
            return ok;
        } catch (Exception e) {
            log.error("[PAYPAL] webhook verify exception", e);
            return false;
        }
    }

    @Override
    public WebhookEvent parseWebhookEvent(OverseasPaymentGateway gateway, String payload) {
        WebhookEvent event = new WebhookEvent();
        event.rawData = new java.util.HashMap<>();
        try {
            JsonNode node = OM.readTree(payload);
            event.eventId = node.path("id").asText();
            event.eventType = node.path("event_type").asText();
            JsonNode resource = node.path("resource");
            event.paymentId = resource.path("id").asText();
            event.status = mapPaypalOrderStatus(resource.path("status").asText());
            @SuppressWarnings("unchecked")
            Map<String, Object> raw = OM.readValue(payload, Map.class);
            event.rawPayload = raw;
        } catch (Exception e) {
            log.error("[PAYPAL] parseWebhookEvent error", e);
            event.eventType = "unknown";
        }
        return event;
    }

    // ─── HealthCheck ────────────────────────────────────────────────────────

    @Override
    public HealthStatus healthCheck(OverseasPaymentGateway gateway) {
        HealthStatus status = new HealthStatus();
        try {
            String token = acquireToken(gateway);
            String baseUrl = resolveBaseUrl(gateway);
            String resp = HttpUtil.get(baseUrl + "/v2/checkout/orders?page_size=1",
                Map.of("Authorization", "Bearer " + token,
                        "Accept", "application/json"));
            JsonNode node = OM.readTree(resp);
            if (node.has("details") && node.path("details").isArray()) {
                status.healthy = false;
                status.message = "PayPal API error: " + node.path("details").get(0).path("description");
            } else {
                status.healthy = true;
                status.message = "PayPal API reachable";
            }
        } catch (Exception e) {
            status.healthy = false;
            status.message = "PayPal API unreachable: " + e.getMessage();
            log.warn("[PAYPAL] healthCheck failed: {}", e.getMessage());
        }
        status.checkedAt = new Date().toString();
        return status;
    }

    @Override
    public String gatewayCode() {
        return "paypal";
    }

    // ─── Token Management ──────────────────────────────────────────────────

    /**
     * Acquire a PayPal OAuth2 access token, using in-memory cache with 1-hour TTL.
     * Uses client_credentials grant:
     *   POST /v1/oauth2/token  (form-encoded)
     *   Auth: Basic clientId:secretKey
     */
    private String acquireToken(OverseasPaymentGateway gateway) throws java.io.IOException {
        String key = cacheKey(gateway.getSecretKey());
        TokenCacheEntry cached = TOKEN_CACHE.get(key);
        long now = System.currentTimeMillis();
        if (cached != null && cached.expireAtMs > now + 60_000) {
            return cached.token;
        }

        String baseUrl = resolveBaseUrl(gateway);
        String clientId = gateway.getPublishableKey(); // PayPal client ID
        String clientSecret = gateway.getSecretKey();

        String basicAuth = "Basic " + Base64.getEncoder().encodeToString(
            (clientId + ":" + clientSecret).getBytes(StandardCharsets.UTF_8));

        String resp = HttpUtil.postForm(
            baseUrl + "/v1/oauth2/token",
            Map.of("grant_type", "client_credentials"),
            Map.of("Authorization", basicAuth,
                    "Content-Type", "application/x-www-form-urlencoded"));

        JsonNode node = OM.readTree(resp);
        String accessToken = node.path("access_token").asText(null);
        int expiresIn = node.path("expires_in").asInt(3600);

        if (accessToken == null || accessToken.isBlank()) {
            throw new java.io.IOException("PayPal token request failed: " + resp);
        }

        TOKEN_CACHE.put(key, new TokenCacheEntry(accessToken, now + expiresIn * 1000L));
        log.debug("[PAYPAL] Token refreshed, expires in {}s", expiresIn);
        return accessToken;
    }

    /**
     * Try to resolve capture_id from paymentId.
     * paymentId may be an order_id — attempt capture to get capture_id.
     */
    private String resolveCaptureId(OverseasPaymentGateway gateway, String token,
                                    String baseUrl, String paymentId) throws java.io.IOException {
        // Try to get the order and find capture_id
        String resp = HttpUtil.get(
            baseUrl + "/v2/checkout/orders/" + paymentId,
            Map.of("Authorization", "Bearer " + token, "Accept", "application/json"));
        JsonNode node = OM.readTree(resp);
        for (JsonNode pu : node.path("purchase_units")) {
            for (JsonNode capture : pu.path("payments").path("captures")) {
                return capture.path("id").asText();
            }
        }
        // If already captured (captured earlier), paymentId IS the capture_id
        return paymentId;
    }

    private String resolveBaseUrl(OverseasPaymentGateway gateway) {
        if (gateway.getApiBaseUrl() != null && !gateway.getApiBaseUrl().isBlank()) {
            return gateway.getApiBaseUrl().replaceAll("/+$", "");
        }
        if ("production".equalsIgnoreCase(gateway.getEnvironment())) {
            return "https://api-m.paypal.com";
        }
        return "https://api-m.sandbox.paypal.com";
    }

    private ObjectNode buildCreateOrderBody(CreatePaymentIntentRequest request) {
        ObjectNode body = OM.createObjectNode();
        body.put("intent", "CAPTURE"); // Authorize + Capture = CAPTURE
        ArrayNode units = body.putArray("purchase_units");
        ObjectNode unit = units.addObject();
        if (request.orderNo != null && !request.orderNo.isBlank()) {
            unit.put("reference_id", request.orderNo);
        }
        if (request.description != null && !request.description.isBlank()) {
            unit.putPOJO("description", request.description);
        }
        ObjectNode amount = unit.putObject("amount");
        String currency = request.currency != null ? request.currency.toUpperCase() : "USD";
        amount.put("currency_code", currency);
        amount.put("value", request.amount != null
            ? request.amount.setScale(2, java.math.RoundingMode.HALF_UP).toPlainString()
            : "0.00");
        // Payer info
        ObjectNode payer = body.putObject("payer");
        if (request.customerEmail != null && !request.customerEmail.isBlank()) {
            payer.put("email_address", request.customerEmail);
        }
        // Application context — redirect URLs
        ObjectNode ctx = body.putObject("application_context");
        if (request.returnUrl != null && !request.returnUrl.isBlank()) {
            ctx.put("return_url", request.returnUrl);
        }
        if (request.cancelUrl != null && !request.cancelUrl.isBlank()) {
            ctx.put("cancel_url", request.cancelUrl);
        }
        return body;
    }

    private String mapPaypalOrderStatus(String ppStatus) {
        if (ppStatus == null) return "UNKNOWN";
        return switch (ppStatus.toUpperCase()) {
            case "APPROVED", "COMPLETED"          -> "succeeded";
            case "CREATED", "SAVED"                -> "CREATED";
            case "PAYER_ACTION_REQUIRED"          -> "requires_payment_method";
            case "VOIDED"                          -> "voided";
            default                                -> ppStatus;
        };
    }

    private String mapPaypalRefundStatus(String ppStatus) {
        if (ppStatus == null) return "UNKNOWN";
        return switch (ppStatus.toUpperCase()) {
            case "COMPLETED"  -> "succeeded";
            case "PENDING"    -> "pending";
            default           -> ppStatus;
        };
    }

    private String cacheKey(String secretKey) {
        return Integer.toHexString(secretKey.hashCode());
    }

    private static class TokenCacheEntry {
        final String token;
        final long expireAtMs;
        TokenCacheEntry(String token, long expireAtMs) {
            this.token = token;
            this.expireAtMs = expireAtMs;
        }
    }
}
