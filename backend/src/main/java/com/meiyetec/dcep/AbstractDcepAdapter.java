package com.meiyetec.dcep;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.security.MessageDigest;
import java.util.Base64;
import java.util.Map;

/**
 * P0-MONEY-001 后续 · DCEP 适配器抽象基类
 *
 * <p>为建行/工行/农行 + 聚合支付服务商提供共享工具：
 * <ul>
 *   <li>SHA-256 签名 + 验签</li>
 *   <li>统一错误码映射（{@link #mapError(String)}）</li>
 *   <li>JSON 序列化（回调原 body 存档）</li>
 *   <li>时间戳校验（防重放）</li>
 * </ul>
 *
 * <p>各家银行继承此抽象类，只需实现 5 个核心方法（prepay/query/refund/close/parseAndVerifyNotify）。
 */
public abstract class AbstractDcepAdapter implements DcepChannelAdapter {

    protected final Logger log = LoggerFactory.getLogger(getClass());
    protected static final ObjectMapper JSON = new ObjectMapper();

    /** 异步通知时间戳允许偏差（秒） */
    protected static final long NOTIFY_TIMESTAMP_TOLERANCE_SECONDS = 300;

    /**
     * 计算 SHA-256 签名（用于银行回调验签）
     *
     * @param algorithm "SHA256" / "RSA-SHA256" / "SM3"
     * @param payload   业务字段拼接（按银行约定的字段顺序）
     * @param secretKey 商户密钥或银行公钥
     * @return Base64 编码的签名
     */
    protected String computeSign(String algorithm, String payload, String secretKey) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            md.update(payload.getBytes("UTF-8"));
            if (secretKey != null) {
                md.update(secretKey.getBytes("UTF-8"));
            }
            return Base64.getEncoder().encodeToString(md.digest());
        } catch (Exception e) {
            throw new RuntimeException("computeSign failed", e);
        }
    }

    /**
     * 解析原始 body 为 Map（容错处理）
     */
    protected Map<String, Object> parseJson(String rawBody) {
        try {
            if (rawBody == null || rawBody.isEmpty()) {
                return Map.of();
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> map = JSON.readValue(rawBody, Map.class);
            return map;
        } catch (Exception e) {
            log.warn("[DCEP] parseJson failed: {}", e.getMessage());
            return Map.of();
        }
    }

    /**
     * 通用错误码映射
     *
     * <p>各银行返回的错误码（数字/字符串）统一映射为 {@link #mapError(String)} 的常量。
     * <p>子类可覆盖此方法，针对特定银行做更细的映射。
     */
    protected String mapError(String bankErrorCode) {
        if (bankErrorCode == null) return "BANK_SYSTEM_ERROR";
        switch (bankErrorCode) {
            case "00000":
            case "SUCCESS":
            case "OK":
                return "OK";
            case "INVALID_SIGNATURE":
            case "INVALID_SIGN":
                return "INVALID_SIGN";
            case "BAD_REQUEST":
            case "INVALID_PARAMS":
            case "INVALID_ARGUMENT":
                return "INVALID_PARAMS";
            case "MERCHANT_NOT_EXIST":
            case "MERCHANT_NOT_FOUND":
                return "MERCHANT_NOT_FOUND";
            case "BALANCE_NOT_ENOUGH":
            case "INSUFFICIENT_BALANCE":
                return "INSUFFICIENT_BALANCE";
            case "ORDER_EXPIRED":
                return "ORDER_EXPIRED";
            case "USER_CANCELED":
            case "USER_CANCEL":
                return "USER_CANCEL";
            case "RISK_BLOCK":
            case "RISK_REJECTED":
                return "RISK_REJECTED";
            case "TOO_MANY_REQUESTS":
            case "RATE_LIMIT":
                return "RATE_LIMIT";
            default:
                return "BANK_SYSTEM_ERROR";
        }
    }

    /**
     * 校验异步通知时间戳（防重放）
     */
    protected boolean isTimestampValid(String timestamp) {
        if (timestamp == null) return false;
        try {
            long ts = Long.parseLong(timestamp);
            long now = System.currentTimeMillis() / 1000;
            return Math.abs(now - ts) <= NOTIFY_TIMESTAMP_TOLERANCE_SECONDS;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    /**
     * 通用 prepay 失败结果
     */
    protected PrepayResult failPrepay(String errorCode, String errorMsg) {
        return PrepayResult.builder()
            .success(false)
            .errorCode(errorCode)
            .errorMsg(errorMsg)
            .build();
    }
}
