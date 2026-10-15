package com.meiyetec.cn.domestic.adapter;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.meiyetec.cn.domestic.DomesticPayChannelAdapter;
import com.meiyetec.cn.domestic.DomesticPayProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

/**
 * 国内支付-银联云闪付（UnionPay Acp）适配器（沙箱 stub / 待接 SDK）
 *
 * <p>**当前状态**：本期仅实现"完整契约 + 沙箱 mock + 银联 SHA-256 验签本地算法"。
 * 真实接入待二期：
 * <ol>
 *   <li>引入 {@code com.unionpay.acp:acp-sdk}（或第三方开源实现 {@code unionpay-acp-sdk}）</li>
 *   <li>替换 {@link #prepay} 的 mock 为 SDK 的 {@code frontConsume} 调用</li>
 *   <li>替换 {@link #parseAndVerifyNotify} 的本地 SHA-256 为 SDK 的 {@code verify}（签名 + 证书链校验）</li>
 * </ol>
 *
 * <p>**本期关键设计**：
 * <ul>
 *   <li>渠道码 {@code UNION_PAY}，与海外 {@code unionpay} 网关编码不冲突</li>
 *   <li>支持 {@code @ConditionalOnProperty(app.cn-pay.union.enabled=true)} 默认禁用（保留沙箱不生效）</li>
 *   <li>异步通知是 {@code application/x-www-form-urlencoded}，验签字符串 = SHA-256(签名密钥 + 待签名元素按 key 排序拼接)</li>
 * </ul>
 *
 * @since 2026-09-07
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "app.cn-pay.union.enabled", havingValue = "true", matchIfMissing = false)
public class UnionPayAcpAdapter implements DomesticPayChannelAdapter {

    private static final ObjectMapper OM = new ObjectMapper();

    /** 银联异步通知必含字段（验签拼接用） */
    private static final String[] SIGN_FIELDS = {
            "merId", "orderId", "txnTime", "respCode", "queryId",
            "settleDate", "txnAmt", "currencyCode", "payCardNo"
    };

    private final DomesticPayProperties properties;
    private final boolean sandboxMode;

    public UnionPayAcpAdapter(DomesticPayProperties properties) {
        this.properties = properties;
        this.sandboxMode = isSandboxMode(properties);
        if (sandboxMode) {
            log.warn("[CN-UNION] SANDBOX mode enabled — 凭据未配置或 union.enabled=false，" +
                    "prepay/query/refund 返回 mock 结果；二期接入 unionpay-acp-sdk");
        } else {
            log.info("[CN-UNION] PROD mode — 凭据已配置，但实装代码待二期补（当前 sandbox-only）");
        }
    }

    private boolean isSandboxMode(DomesticPayProperties p) {
        DomesticPayProperties.Union u = p.getUnion();
        return !u.isEnabled()
                || u.getMerId() == null || u.getMerId().isBlank() || u.getMerId().contains("PLACEHOLDER")
                || u.getSignCertPwd() == null || u.getSignCertPwd().isBlank()
                || "sandbox".equalsIgnoreCase(p.getEnvMode());
    }

    @Override
    public String channelCode() {
        return "UNION_PAY";
    }

    // ============================================================
    // 1. prepay（前台消费 — 二维码扫码付）
    // ============================================================
    @Override
    public PrepayResult prepay(PrepayRequest req) {
        String prepayId = "MOCK-UNION-" + Instant.now().getEpochSecond() + "-"
                + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        String qrCode = "unionpay://walletpay?pr=" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        log.info("[CN-UNION-MOCK] prepay: outOrderNo={} prepayId={}", req.getOutOrderNo(), prepayId);
        return PrepayResult.builder()
                .success(true)
                .prepayId(prepayId)
                .qrCode(qrCode)
                .expiresAt(req.getExpireAt() == null
                        ? LocalDateTime.now().plusMinutes(15)
                                .atZone(ZoneOffset.ofHours(8))
                                .format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)
                        : req.getExpireAt())
                .build();
    }

    // ============================================================
    // 2. query
    // ============================================================
    @Override
    public QueryResult query(OrderQueryRequest req) {
        return QueryResult.builder().success(true).status("WAIT_PAY").build();
    }

    // ============================================================
    // 3. refund（二期接入 unionpay-acp-sdk Refund API）
    // ============================================================
    @Override
    public RefundResult refund(RefundRequest req) {
        log.warn("[CN-UNION] refund stub — 二期接入: outTradeNo={} refundNo={}",
                req.getOutOrderNo(), req.getRefundOrderNo());
        return RefundResult.builder()
                .success(false)
                .errorCode("UNION_REFUND_NOT_IMPLEMENTED")
                .errorMsg("二期接入 unionpay-acp-sdk 的 frontConsume / backConsume 退款流程")
                .build();
    }

    @Override
    public CloseResult closeOrder(CloseRequest req) {
        return CloseResult.builder().success(true).build();
    }

    // ============================================================
    // 4. parseAndVerifyNotify
    //    沙箱：签名直通；生产：unionpay-acp-sdk 的 SDK.verify
    //
    //    银联 Acp 验签元素（按 SIGN_FIELDS 顺序，key 字典序）拼接成字符串，
    //    再用 SHA-256(签名密钥 + 待签名串) 与 signature 比对
    // ============================================================
    @Override
    public NotifyResult parseAndVerifyNotify(String rawBody, Map<String, String> headers) {
        try {
            String merId = headers.getOrDefault("merId", properties.getUnion().getMerId());
            String orderId = headers.get("orderId");
            String txnTime = headers.get("txnTime");
            String respCode = headers.getOrDefault("respCode", "00");
            String queryId = headers.getOrDefault("queryId", "MOCK-UNION-Q-" + UUID.randomUUID());
            String txnAmt = headers.getOrDefault("txnAmt", "0");
            String currencyCode = headers.getOrDefault("currencyCode", "156"); // 156=CNY

            // 沙箱模式直接签名通过
            boolean signatureValid = sandboxMode;
            if (!sandboxMode) {
                String signature = headers.get("signature");
                if (signature != null && !signature.isBlank()
                        && properties.getUnion().getSignCertPwd() != null) {
                    String signed = concatSignFields(headers);
                    String expected = sha256(properties.getUnion().getSignCertPwd() + signed);
                    signatureValid = signature.equalsIgnoreCase(expected);
                }
            }

            boolean isSuccess = "00".equals(respCode);
            long amountCents = parseAmountToCents(txnAmt);

            log.info("[CN-UNION] notify: orderId={} respCode={} amount={} signValid={}",
                    orderId, respCode, amountCents, signatureValid);

            return NotifyResult.builder()
                    .signatureValid(signatureValid)
                    .eventType(isSuccess ? "PAY_SUCCESS" : "PAY_FAILED")
                    .notifyId(queryId)
                    .outOrderNo(orderId)
                    .transactionId(queryId)
                    .amountCents(amountCents)
                    .rawBody(rawBody)
                    .build();
        } catch (Exception e) {
            log.error("[CN-UNION] notify parse failed", e);
            return NotifyResult.builder()
                    .signatureValid(false)
                    .rawBody(rawBody)
                    .errorMsg("Parse exception: " + e.getMessage())
                    .build();
        }
    }

    /** 拼接待签名字段：merId=...&orderId=...&txnTime=... 按字典序（这里使用固定顺序 SIGN_FIELDS） */
    private String concatSignFields(Map<String, String> headers) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < SIGN_FIELDS.length; i++) {
            String key = SIGN_FIELDS[i];
            String value = headers.getOrDefault(key, "");
            if (i > 0) sb.append("&");
            sb.append(key).append("=").append(value);
        }
        return sb.toString();
    }

    private String sha256(String input) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        byte[] hash = md.digest(input.getBytes(StandardCharsets.UTF_8));
        return Base64.getEncoder().encodeToString(hash);
    }

    /** 银联 txnAmt 单位为"分"字符串，直接转 long */
    private long parseAmountToCents(String txnAmt) {
        try {
            return Long.parseLong(txnAmt);
        } catch (NumberFormatException e) {
            return 0L;
        }
    }
}
