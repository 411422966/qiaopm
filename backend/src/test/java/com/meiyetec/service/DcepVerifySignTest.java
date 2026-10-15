package com.meiyetec.service;

import com.meiyetec.config.DcepConfigProperties;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * P0-MONEY-001 · DcepVerifySignTest：验签规则测试
 *
 * <p>验证 DcepService.verifySign 在 dev/prod 模式下的行为：
 * <ul>
 *   <li>SIGN-1: dev 模式 + 正确 SHA-256 签名 → 通过</li>
 *   <li>SIGN-2: dev 模式 + MOCK- 前缀签名 → 通过</li>
 *   <li>SIGN-3: dev 模式 + 错误签名 → 拒绝</li>
 *   <li>SIGN-4: dev 模式 + null 签名 → 拒绝</li>
 *   <li>SIGN-5: prod 模式 + MOCK- 前缀签名 → 拒绝（fail-safe）</li>
 *   <li>SIGN-6: prod 模式 + 正确 SHA-256 签名 → 通过</li>
 *   <li>SIGN-7: prod 模式 + 错误签名 → 拒绝</li>
 *   <li>SIGN-8: 大小写不敏感（SHA-256 比对忽略大小写）</li>
 * </ul>
 */
@SpringBootTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class DcepVerifySignTest {

    @Autowired
    private DcepService dcepService;

    @Autowired
    private DcepConfigProperties dcepConfig;

    private static final String CHANNEL = "DCEP-CCB-PROD";
    private static final String NOTIFY_ID = "NOTIFY-20260814120001";
    private static final String DCEP_ORDER_NO = "DCEP20260814120001";

    @AfterEach
    void resetMode() {
        dcepConfig.setMode("dev");
    }

    private Map<String, Object> buildPayload() {
        Map<String, Object> payload = new HashMap<>();
        payload.put("notifyId", NOTIFY_ID);
        payload.put("dcepOrderNo", DCEP_ORDER_NO);
        payload.put("eventType", "PAY_SUCCESS");
        return payload;
    }

    @Test
    @Order(1)
    @DisplayName("SIGN-1: dev 模式 + 正确 SHA-256 签名 → 拒绝（fail-fast，P0-T12 收紧）")
    void sign_dev_correct_sha256() {
        dcepConfig.setMode("dev");
        Map<String, Object> payload = buildPayload();
        String correctSign = dcepService.computeSha256Sign(CHANNEL, NOTIFY_ID, DCEP_ORDER_NO);

        // P0-T12：dev 模式必须完全 demo-only。任何非 MOCK- 前缀的真实签名（含正确 SHA-256）
        // 一律 fail-fast — 禁止"用 dev 模式挂着正式域名接受真银行回调"。
        assertFalse(dcepService.verifySign(CHANNEL, payload, correctSign),
                "P0-T12 dev 模式应拒绝外部真实签名（含正确 SHA-256），fail-fast 阻断 dev 接受真银行回调");
    }

    @Test
    @Order(2)
    @DisplayName("SIGN-2: dev 模式 + MOCK- 前缀签名 → 通过")
    void sign_dev_mock_prefix() {
        dcepConfig.setMode("dev");
        Map<String, Object> payload = buildPayload();

        assertTrue(dcepService.verifySign(CHANNEL, payload, "MOCK-abcdef123"),
                "dev 模式 MOCK- 前缀应通过");
    }

    @Test
    @Order(3)
    @DisplayName("SIGN-3: dev 模式 + 错误签名 → 拒绝")
    void sign_dev_wrong_signature() {
        dcepConfig.setMode("dev");
        Map<String, Object> payload = buildPayload();

        assertFalse(dcepService.verifySign(CHANNEL, payload, "wrong-signature-value"),
                "错误签名应被拒绝");
        assertFalse(dcepService.verifySign(CHANNEL, payload, "a1b2c3d4e5"),
                "任意错误签名应被拒绝");
    }

    @Test
    @Order(4)
    @DisplayName("SIGN-4: dev 模式 + null 签名 → 拒绝")
    void sign_dev_null() {
        dcepConfig.setMode("dev");
        Map<String, Object> payload = buildPayload();

        assertFalse(dcepService.verifySign(CHANNEL, payload, null),
                "null 签名应被拒绝");
    }

    @Test
    @Order(5)
    @DisplayName("SIGN-5: prod 模式 + MOCK- 前缀签名 → 拒绝（关键 fail-safe）")
    void sign_prod_rejects_mock_prefix() {
        dcepConfig.setMode("prod");
        Map<String, Object> payload = buildPayload();

        assertFalse(dcepService.verifySign(CHANNEL, payload, "MOCK-abcdef123"),
                "prod 模式必须拒绝 MOCK- 前缀签名");
        assertFalse(dcepService.verifySign(CHANNEL, payload, "MOCK-"),
                "prod 模式必须拒绝任何 MOCK- 前缀");
        assertFalse(dcepService.verifySign(CHANNEL, payload, "mock-test"),
                "prod 模式不区分大小写，mock- 前缀也拒绝");
    }

    @Test
    @Order(6)
    @DisplayName("SIGN-6: prod 模式 + 正确 SHA-256 签名 → 通过")
    void sign_prod_correct_sha256() {
        dcepConfig.setMode("prod");
        Map<String, Object> payload = buildPayload();
        String correctSign = dcepService.computeSha256Sign(CHANNEL, NOTIFY_ID, DCEP_ORDER_NO);

        assertTrue(dcepService.verifySign(CHANNEL, payload, correctSign),
                "prod 模式正确 SHA-256 签名应通过");
    }

    @Test
    @Order(7)
    @DisplayName("SIGN-7: prod 模式 + 错误签名 → 拒绝")
    void sign_prod_wrong_signature() {
        dcepConfig.setMode("prod");
        Map<String, Object> payload = buildPayload();

        assertFalse(dcepService.verifySign(CHANNEL, payload, "wrong-signature-value"),
                "prod 模式错误签名应被拒绝");
    }

    @Test
    @Order(8)
    @DisplayName("SIGN-8: dev 模式 + 大写真实 SHA-256 → 拒绝（fail-fast，P0-T12 收紧）")
    void sign_case_insensitive() {
        dcepConfig.setMode("dev");
        Map<String, Object> payload = buildPayload();
        String lower = dcepService.computeSha256Sign(CHANNEL, NOTIFY_ID, DCEP_ORDER_NO);
        String upper = lower.toUpperCase();

        // P0-T12：dev 模式完全 demo-only。大小写不影响结论——任何非 MOCK- 前缀签名必拒。
        assertFalse(dcepService.verifySign(CHANNEL, payload, upper),
                "P0-T12 dev 模式应拒绝大写 SHA-256 真实签名（fail-fast）");
        assertFalse(dcepService.verifySign(CHANNEL, payload, lower),
                "P0-T12 dev 模式应拒绝小写 SHA-256 真实签名（fail-fast）");
    }

    @Test
    @Order(9)
    @DisplayName("SIGN-9: 不同 notifyId 应产生不同签名")
    void sign_different_notifyid() {
        dcepConfig.setMode("dev");
        String s1 = dcepService.computeSha256Sign(CHANNEL, "NOTIFY-001", DCEP_ORDER_NO);
        String s2 = dcepService.computeSha256Sign(CHANNEL, "NOTIFY-002", DCEP_ORDER_NO);

        assertNotEquals(s1, s2, "不同 notifyId 应产生不同签名");
    }

    @Test
    @Order(10)
    @DisplayName("SIGN-10: 不同 channelCode 应产生不同签名")
    void sign_different_channel() {
        dcepConfig.setMode("dev");
        String s1 = dcepService.computeSha256Sign("DCEP-CCB", NOTIFY_ID, DCEP_ORDER_NO);
        String s2 = dcepService.computeSha256Sign("DCEP-ICBC", NOTIFY_ID, DCEP_ORDER_NO);

        assertNotEquals(s1, s2, "不同 channelCode 应产生不同签名");
    }

    @Test
    @Order(11)
    @DisplayName("SIGN-11: dev 模式 + 外部真实签名 → fail-fast（P0-T12 收紧）")
    void sign_dev_real_signature_rejected() {
        dcepConfig.setMode("dev");
        Map<String, Object> payload = buildPayload();

        // P0-T12：dev 模式 fail-fast 场景。任何非 MOCK- 前缀的真实签名一律拒绝。
        String realSign = dcepService.computeSha256Sign(CHANNEL, NOTIFY_ID, DCEP_ORDER_NO);
        assertFalse(dcepService.verifySign(CHANNEL, payload, realSign),
                "dev 模式必须拒绝任何非 MOCK- 前缀的真实银行签名");

        // 反向验证：MOCK- 前缀仍然接受（保证现有 dev 业务不变）
        assertTrue(dcepService.verifySign(CHANNEL, payload, "MOCK-anything"),
                "dev 模式 + MOCK- 前缀仍应通过（兼容现有 demo 行为）");
    }
}
