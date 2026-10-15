package com.meiyetec.dcep.operator;

import com.meiyetec.dcep.crypto.SmCryptoProvider;
import com.meiyetec.dcep.operator.DcepOperatorProperties.Operator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.*;

/**
 * DCEP-03/04 阶段 B：运营机构报文签名服务（双算法 SM2/RSA）
 *
 * <p>各运营机构对签名算法有不同要求：
 * <ul>
 *   <li>工行（ICBC）：RSA-2048 + SHA-256（开放平台 REST）</li>
 *   <li>建行（CCB）：RSA + SHA-256（对公网银 V14.1）</li>
 *   <li>农行（ABC）：RSA + SHA-256（网络支付接口）</li>
 *   <li>试点扩展：可切换 SM2 + SM3 国密算法</li>
 * </ul>
 *
 * <p>本服务统一封装 3 类操作：
 * <ol>
 *   <li>{@link #sign 报文加签}（我方 → 银行）</li>
 *   <li>{@link #verifyNotify 异步通知验签}（银行 → 我方）</li>
 *   <li>{@link #buildCanonicalString 报文规范化}（按字段字典序拼接）</li>
 * </ol>
 */
@Slf4j
@Service
public class OperatorSignatureService {

    @Autowired(required = false)
    private SmCryptoProvider smCrypto;

    /**
     * 报文规范化（按字段 key 字典序拼接）
     *
     * <p>各家银行的拼接规范略有不同，但基本都遵循字典序：
     * <pre>
     *   key1=value1&key2=value2&...&keyN=valueN
     * </pre>
     *
     * @param fields    待签名字段（key → value，value 必须已转 String）
     * @param exclude   排除的字段（如 sign / sign_type）
     */
    public String buildCanonicalString(Map<String, String> fields, Set<String> exclude) {
        if (fields == null || fields.isEmpty()) return "";
        TreeMap<String, String> sorted = new TreeMap<>(fields);
        if (exclude != null) sorted.keySet().removeAll(exclude);
        StringBuilder sb = new StringBuilder();
        boolean first = true;
        for (Map.Entry<String, String> e : sorted.entrySet()) {
            if (!first) sb.append("&");
            sb.append(e.getKey()).append("=").append(e.getValue() == null ? "" : e.getValue());
            first = false;
        }
        return sb.toString();
    }

    /**
     * 加签（我方 → 银行）
     *
     * @param operator 运营机构配置（含算法 + 私钥）
     * @param payload  业务字段（不含 sign 字段本身）
     * @return Base64 编码的签名
     */
    public String sign(Operator operator, Map<String, String> payload) {
        String canonical = buildCanonicalString(payload, Set.of("sign", "sign_type"));
        String algo = operator.getSignAlgorithm() == null ? "RSA-SHA256" : operator.getSignAlgorithm();
        return switch (algo) {
            case "RSA-SHA256", "RSA" -> rsaSha256Sign(canonical, operator.getMerchantPrivateKey());
            case "SM2-SM3", "SM2" -> sm2Sign(canonical, operator.getMerchantPrivateKey(),
                    operator.getMerchantId());
            case "HMAC-SHA256" -> hmacSha256Sign(canonical, operator.getMerchantPrivateKey());
            default -> throw new IllegalArgumentException("不支持的签名算法: " + algo);
        };
    }

    /**
     * 验签（银行 → 我方）
     */
    public boolean verifyNotify(Operator operator, Map<String, String> notifyFields,
                                 String signature) {
        if (signature == null || signature.isEmpty()) return false;
        String canonical = buildCanonicalString(notifyFields, Set.of("sign", "sign_type", "signature"));
        String algo = operator.getSignAlgorithm() == null ? "RSA-SHA256" : operator.getSignAlgorithm();
        return switch (algo) {
            case "RSA-SHA256", "RSA" -> rsaSha256Verify(canonical, signature, operator.getBankPublicKey());
            case "SM2-SM3", "SM2" -> sm2Verify(canonical, signature, operator.getBankPublicKey(),
                    operator.getMerchantId());
            case "HMAC-SHA256" -> hmacSha256Verify(canonical, signature, operator.getMerchantPrivateKey());
            default -> {
                log.warn("[DCEP] 不支持的验签算法: {}", algo);
                yield false;
            }
        };
    }

    // ============================================================
    // RSA-SHA256
    // ============================================================

    private String rsaSha256Sign(String canonical, String privateKeyPem) {
        if (privateKeyPem == null || privateKeyPem.isEmpty()) {
            throw new IllegalArgumentException("商户私钥未配置");
        }
        try {
            KeyFactory kf = KeyFactory.getInstance("RSA");
            byte[] keyBytes = pemToBytes(privateKeyPem);
            PKCS8EncodedKeySpec spec = new PKCS8EncodedKeySpec(keyBytes);
            PrivateKey priv = kf.generatePrivate(spec);
            Signature sig = Signature.getInstance("SHA256withRSA");
            sig.initSign(priv);
            sig.update(canonical.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(sig.sign());
        } catch (Exception e) {
            throw new RuntimeException("RSA sign failed: " + e.getMessage(), e);
        }
    }

    private boolean rsaSha256Verify(String canonical, String signatureBase64, String publicKeyPem) {
        if (publicKeyPem == null || publicKeyPem.isEmpty()) {
            log.warn("[DCEP] 银行公钥未配置");
            return false;
        }
        try {
            KeyFactory kf = KeyFactory.getInstance("RSA");
            byte[] keyBytes = pemToBytes(publicKeyPem);
            X509EncodedKeySpec spec = new X509EncodedKeySpec(keyBytes);
            PublicKey pub = kf.generatePublic(spec);
            Signature sig = Signature.getInstance("SHA256withRSA");
            sig.initVerify(pub);
            sig.update(canonical.getBytes(StandardCharsets.UTF_8));
            return sig.verify(Base64.getDecoder().decode(signatureBase64));
        } catch (Exception e) {
            log.debug("[DCEP] RSA verify failed: {}", e.getMessage());
            return false;
        }
    }

    // ============================================================
    // SM2-SM3（基于阶段 A 的 SmCryptoProvider）
    // ============================================================

    private String sm2Sign(String canonical, String privateKeyPem, String userId) {
        if (smCrypto == null) {
            throw new IllegalStateException("SmCryptoProvider 未配置，无法执行 SM2 签名");
        }
        if (privateKeyPem == null || privateKeyPem.isEmpty()) {
            throw new IllegalArgumentException("商户私钥未配置");
        }
        try {
            // SM2 私钥：HEX 字符串格式（64 字符）
            BigInteger privD = new BigInteger(privateKeyPem, 16);
            // 公钥需要从其他渠道获取（实际生产中预先生成密钥对并部署）
            // 这里约定 publicKeyPem 也是 HEX 格式
            byte[] pubBytes = hexToBytes(privateKeyPem + "_pub");  // 占位
            return smCrypto.sm2Sign(privD, pubBytes, canonical.getBytes(StandardCharsets.UTF_8)).toHex();
        } catch (Exception e) {
            throw new RuntimeException("SM2 sign failed: " + e.getMessage(), e);
        }
    }

    private boolean sm2Verify(String canonical, String signatureHex, String publicKeyPem,
                                String userId) {
        if (smCrypto == null || publicKeyPem == null) return false;
        try {
            byte[] pubBytes = hexToBytes(publicKeyPem);
            return smCrypto.sm2Verify(pubBytes, canonical.getBytes(StandardCharsets.UTF_8),
                    com.meiyetec.dcep.crypto.Sm2Signature.fromConcat(hexToBytes(signatureHex)));
        } catch (Exception e) {
            log.debug("[DCEP] SM2 verify failed: {}", e.getMessage());
            return false;
        }
    }

    // ============================================================
    // HMAC-SHA256（少部分机构用）
    // ============================================================

    private String hmacSha256Sign(String canonical, String secret) {
        if (secret == null || secret.isEmpty()) {
            throw new IllegalArgumentException("HMAC secret 未配置");
        }
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] out = mac.doFinal(canonical.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(out);
        } catch (Exception e) {
            throw new RuntimeException("HMAC sign failed: " + e.getMessage(), e);
        }
    }

    private boolean hmacSha256Verify(String canonical, String signatureBase64, String secret) {
        if (secret == null) return false;
        String expected = hmacSha256Sign(canonical, secret);
        return constantTimeEquals(expected, signatureBase64);
    }

    // ============================================================
    // 工具
    // ============================================================

    /** 从 PEM 字符串中提取 DER 字节（去除 BEGIN/END 头和换行） */
    private byte[] pemToBytes(String pem) {
        String clean = pem.replaceAll("-----BEGIN [^-]+-----", "")
                .replaceAll("-----END [^-]+-----", "")
                .replaceAll("\\s+", "");
        return Base64.getDecoder().decode(clean);
    }

    private byte[] hexToBytes(String hex) {
        int len = hex.length();
        byte[] out = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            out[i / 2] = (byte) ((Character.digit(hex.charAt(i), 16) << 4)
                    + Character.digit(hex.charAt(i + 1), 16));
        }
        return out;
    }

    /** 常量时间字符串比较（防时序攻击） */
    private boolean constantTimeEquals(String a, String b) {
        if (a == null || b == null) return false;
        if (a.length() != b.length()) return false;
        int diff = 0;
        for (int i = 0; i < a.length(); i++) {
            diff |= a.charAt(i) ^ b.charAt(i);
        }
        return diff == 0;
    }
}
