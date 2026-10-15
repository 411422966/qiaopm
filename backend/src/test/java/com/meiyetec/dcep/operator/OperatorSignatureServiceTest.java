package com.meiyetec.dcep.operator;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * DCEP-03/04 阶段 B：运营机构报文签名服务单元测试
 *
 * <p>覆盖：
 * <ol>
 *   <li>报文规范化（字段字典序拼接）</li>
 *   <li>RSA-SHA256 加签 + 验签</li>
 *   <li>HMAC-SHA256 加签 + 验签</li>
 *   <li>篡改后验签失败</li>
 *   <li>缺少字段（sign / sign_type）不参与规范化</li>
 * </ol>
 */
@DisplayName("DCEP-03/04 阶段 B OperatorSignatureService")
class OperatorSignatureServiceTest {

    private final OperatorSignatureService svc = new OperatorSignatureService();

    private DcepOperatorProperties.Operator rsaOperator(KeyPair kp) throws Exception {
        DcepOperatorProperties.Operator op = new DcepOperatorProperties.Operator();
        op.setMerchantId("M100");
        op.setSignAlgorithm("RSA-SHA256");
        op.setMerchantPrivateKey(encodePem(kp.getPrivate().getEncoded(), true));
        op.setBankPublicKey(encodePem(kp.getPublic().getEncoded(), false));
        return op;
    }

    private DcepOperatorProperties.Operator hmacOperator(String secret) {
        DcepOperatorProperties.Operator op = new DcepOperatorProperties.Operator();
        op.setMerchantId("M200");
        op.setSignAlgorithm("HMAC-SHA256");
        op.setMerchantPrivateKey(secret); // HMAC 共用 secret
        return op;
    }

    private String encodePem(byte[] der, boolean isPrivate) {
        String b64 = Base64.getEncoder().encodeToString(der);
        String header = isPrivate ? "-----BEGIN PRIVATE KEY-----" : "-----BEGIN PUBLIC KEY-----";
        String footer = isPrivate ? "-----END PRIVATE KEY-----" : "-----END PUBLIC KEY-----";
        return header + "\n" + b64 + "\n" + footer;
    }

    private KeyPair generateRsa() throws Exception {
        KeyPairGenerator gen = KeyPairGenerator.getInstance("RSA");
        gen.initialize(2048);
        return gen.generateKeyPair();
    }

    @Test
    @DisplayName("报文规范化：字段按字典序拼接，sign / sign_type 被排除")
    void canonical_basic() {
        Map<String, String> m = new HashMap<>();
        m.put("b", "2");
        m.put("a", "1");
        m.put("c", "3");
        m.put("sign", "SHOULD_NOT_INCLUDE");
        m.put("sign_type", "RSA");
        String canon = svc.buildCanonicalString(m, java.util.Set.of("sign", "sign_type"));
        assertEquals("a=1&b=2&c=3", canon);
    }

    @Test
    @DisplayName("RSA-SHA256 加签 + 验签完整闭环")
    void rsaSignVerify() throws Exception {
        KeyPair kp = generateRsa();
        DcepOperatorProperties.Operator op = rsaOperator(kp);
        Map<String, String> payload = new HashMap<>();
        payload.put("merchantId", "M100");
        payload.put("orderNo", "ORDER-001");
        payload.put("amountCents", "10000");
        String sig = svc.sign(op, payload);
        assertNotNull(sig);
        assertFalse(sig.isEmpty());
        assertTrue(svc.verifyNotify(op, payload, sig), "RSA 验签必须通过");
    }

    @Test
    @DisplayName("RSA 篡改验签失败")
    void rsaTamperedVerify() throws Exception {
        KeyPair kp = generateRsa();
        DcepOperatorProperties.Operator op = rsaOperator(kp);
        Map<String, String> payload = new HashMap<>();
        payload.put("merchantId", "M100");
        payload.put("amountCents", "10000");
        String sig = svc.sign(op, payload);

        Map<String, String> tampered = new HashMap<>(payload);
        tampered.put("amountCents", "99999");  // 篡改金额
        assertFalse(svc.verifyNotify(op, tampered, sig), "金额被篡改后验签必须失败");
    }

    @Test
    @DisplayName("RSA 用错误公钥验签失败")
    void rsaWrongPublicKey() throws Exception {
        KeyPair kp1 = generateRsa();
        KeyPair kp2 = generateRsa();
        DcepOperatorProperties.Operator op1 = rsaOperator(kp1);
        DcepOperatorProperties.Operator op2 = rsaOperator(kp1); // 用 kp1 的私钥签名
        op2.setBankPublicKey(encodePem(kp2.getPublic().getEncoded(), false));  // 但用 kp2 的公钥验签

        Map<String, String> payload = new HashMap<>();
        payload.put("merchantId", "M100");
        String sig = svc.sign(op1, payload);
        assertFalse(svc.verifyNotify(op2, payload, sig), "公钥不匹配应验签失败");
    }

    @Test
    @DisplayName("HMAC-SHA256 加签 + 验签完整闭环")
    void hmacSignVerify() {
        DcepOperatorProperties.Operator op = hmacOperator("SECRET_KEY_123456");
        Map<String, String> payload = new HashMap<>();
        payload.put("merchantId", "M200");
        payload.put("nonce", "abc123");
        String sig = svc.sign(op, payload);
        assertNotNull(sig);
        assertTrue(svc.verifyNotify(op, payload, sig));
    }

    @Test
    @DisplayName("HMAC 用错误 secret 验签失败")
    void hmacWrongSecret() {
        DcepOperatorProperties.Operator op1 = hmacOperator("SECRET_A");
        DcepOperatorProperties.Operator op2 = hmacOperator("SECRET_B");
        Map<String, String> payload = new HashMap<>();
        payload.put("merchantId", "M200");
        String sig = svc.sign(op1, payload);
        assertFalse(svc.verifyNotify(op2, payload, sig));
    }

    @Test
    @DisplayName("不支持的签名算法应抛 IllegalArgumentException")
    void unsupportedAlgo() {
        DcepOperatorProperties.Operator op = new DcepOperatorProperties.Operator();
        op.setSignAlgorithm("UNKNOWN-ALGO");
        op.setMerchantPrivateKey("test");
        Map<String, String> payload = new HashMap<>();
        payload.put("k", "v");
        assertThrows(IllegalArgumentException.class, () -> svc.sign(op, payload));
    }

    @Test
    @DisplayName("signature 为空：验签返回 false")
    void emptySignature() throws Exception {
        KeyPair kp = generateRsa();
        DcepOperatorProperties.Operator op = rsaOperator(kp);
        Map<String, String> payload = new HashMap<>();
        payload.put("k", "v");
        assertFalse(svc.verifyNotify(op, payload, ""));
        assertFalse(svc.verifyNotify(op, payload, null));
    }

    @Test
    @DisplayName("空 payload 规范化结果为空字符串")
    void emptyPayload() {
        assertEquals("", svc.buildCanonicalString(new HashMap<>(), null));
        assertEquals("", svc.buildCanonicalString(null, null));
    }

    @Test
    @DisplayName("RSA 公钥未配置：验签返回 false（fail-closed）")
    void missingPublicKey() throws Exception {
        KeyPair kp = generateRsa();
        DcepOperatorProperties.Operator op = rsaOperator(kp);
        op.setBankPublicKey(null);
        Map<String, String> payload = new HashMap<>();
        payload.put("k", "v");
        assertFalse(svc.verifyNotify(op, payload, "fakesig"));
    }

    @Test
    @DisplayName("RSA 私钥未配置：加签抛 IllegalArgumentException")
    void missingPrivateKey() throws Exception {
        KeyPair kp = generateRsa();
        DcepOperatorProperties.Operator op = rsaOperator(kp);
        op.setMerchantPrivateKey(null);
        Map<String, String> payload = new HashMap<>();
        payload.put("k", "v");
        assertThrows(IllegalArgumentException.class, () -> svc.sign(op, payload));
    }
}
