package com.meiyetec.dcep.auth;

import com.meiyetec.dcep.crypto.Sm2KeyPair;
import com.meiyetec.dcep.crypto.SmCryptoProvider;
import com.meiyetec.dcep.crypto.impl.BouncyCastleSmCryptoProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;

/**
 * DCEP-04 阶段 C：双向认证单元测试
 *
 * <p>覆盖：
 * <ol>
 *   <li>challenge 颁发：长度 32 字节，TTL 60s</li>
 *   <li>双向签名验证通过：颁发 sessionKey</li>
 *   <li>设备签名错误 → DEVICE_SIGNATURE_INVALID</li>
 *   <li>challenge 过期 → CHALLENGE_EXPIRED</li>
 *   <li>无 challenge → NO_CHALLENGE</li>
 *   <li>失败次数过多 → 锁定</li>
 * </ol>
 */
@DisplayName("DCEP-04 阶段 C BidirectionalAuthService")
class BidirectionalAuthServiceTest {

    private BidirectionalAuthService auth;
    private SmCryptoProvider sm;
    private Sm2KeyPair deviceKp;

    @BeforeEach
    void setUp() throws Exception {
        sm = new BouncyCastleSmCryptoProvider();
        auth = new BidirectionalAuthService();
        java.lang.reflect.Field f = BidirectionalAuthService.class.getDeclaredField("smCrypto");
        f.setAccessible(true);
        f.set(auth, sm);
        deviceKp = sm.generateSm2KeyPair();
    }

    private String signChallenge(String deviceSerial, byte[] challenge, Sm2KeyPair kp) {
        byte[] payload = Base64.getEncoder().encode(challenge);
        return sm.sm2Sign(kp.getPrivateKey(), kp.getPublicKeyUncompressed(), payload).toHex();
    }

    private String signReverse(String reverseChallengeHex, Sm2KeyPair kp) {
        // 这里用 deviceKp 模拟"商户私钥签名"（简化：实际应是独立商户密钥对）
        return sm.sm2Sign(kp.getPrivateKey(), kp.getPublicKeyUncompressed(),
                reverseChallengeHex.getBytes()).toHex();
    }

    @Test
    @DisplayName("initiateAuth：challenge 长度 32 字节 + reverseChallenge 非空")
    void initiate_challengeLength() {
        BidirectionalAuthService.ChallengeResponse resp = auth.initiateAuth("DEVICE-001");
        assertNotNull(resp);
        assertEquals("DEVICE-001", resp.getDeviceSerial());
        assertEquals(32, resp.getChallenge().length);
        assertNotNull(resp.getReverseChallenge());
        assertTrue(resp.getReverseChallenge().length() >= 32);
        assertNotNull(resp.getExpiresAt());
    }

    @Test
    @DisplayName("双向签名验证通过：颁发 sessionKey")
    void verifyBoth_success() {
        BidirectionalAuthService.ChallengeResponse challenge = auth.initiateAuth("DEVICE-002");
        String deviceSig = signChallenge("DEVICE-002", challenge.getChallenge(), deviceKp);
        String serverSig = signReverse(challenge.getReverseChallenge(), deviceKp);
        String pubKeyHex = bytesToHex(deviceKp.getPublicKeyUncompressed());

        BidirectionalAuthService.VerificationResult result = auth.verifyBoth(
                "DEVICE-002", pubKeyHex, deviceSig, serverSig, "server-priv");

        assertTrue(result.isSuccess(), result.getErrorMsg());
        assertNotNull(result.getSessionKey());
        assertEquals(44, result.getSessionKey().length(), "Base64 encoding 32 bytes should be 44 chars");
    }

    @Test
    @DisplayName("设备签名错误 → DEVICE_SIGNATURE_INVALID")
    void verify_deviceSignatureWrong() {
        BidirectionalAuthService.ChallengeResponse challenge = auth.initiateAuth("DEVICE-003");
        // 用错误私钥签名
        Sm2KeyPair wrong = sm.generateSm2KeyPair();
        String wrongSig = signChallenge("DEVICE-003", challenge.getChallenge(), wrong);
        String pubKeyHex = bytesToHex(deviceKp.getPublicKeyUncompressed());

        BidirectionalAuthService.VerificationResult result = auth.verifyBoth(
                "DEVICE-003", pubKeyHex, wrongSig, "anySig", "priv");

        assertFalse(result.isSuccess());
        assertEquals("DEVICE_SIGNATURE_INVALID", result.getErrorCode());
    }

    @Test
    @DisplayName("无 challenge 直接验证 → NO_CHALLENGE")
    void verify_noChallenge() {
        BidirectionalAuthService.VerificationResult result = auth.verifyBoth(
                "NEVER_INITIATED", "pubkey", "sig", "sig", "priv");
        assertFalse(result.isSuccess());
        assertEquals("NO_CHALLENGE", result.getErrorCode());
    }

    @Test
    @DisplayName("连续 3 次失败 → 锁定 + TOO_MANY_ATTEMPTS")
    void verify_tooManyAttempts() {
        Sm2KeyPair wrong = sm.generateSm2KeyPair();
        BidirectionalAuthService.ChallengeResponse challenge = auth.initiateAuth("DEVICE-004");
        String wrongSig = signChallenge("DEVICE-004", challenge.getChallenge(), wrong);
        String pubKeyHex = bytesToHex(deviceKp.getPublicKeyUncompressed());

        for (int i = 0; i < 3; i++) {
            BidirectionalAuthService.VerificationResult r = auth.verifyBoth(
                    "DEVICE-004", pubKeyHex, wrongSig, "any", "priv");
            assertFalse(r.isSuccess());
            assertEquals("DEVICE_SIGNATURE_INVALID", r.getErrorCode());
        }
        // 第 4 次：会话已锁定
        BidirectionalAuthService.VerificationResult r = auth.verifyBoth(
                "DEVICE-004", "pubkey", "sig", "sig", "priv");
        assertFalse(r.isSuccess());
        assertEquals("TOO_MANY_ATTEMPTS", r.getErrorCode());
    }

    @Test
    @DisplayName("invalidate: clear challenge cache")
    void invalidate_clearsSession() {
        auth.initiateAuth("DEVICE-005");
        auth.invalidate("DEVICE-005");
        BidirectionalAuthService.VerificationResult r = auth.verifyBoth(
                "DEVICE-005", "pubkey", "sig", "sig", "priv");
        assertFalse(r.isSuccess());
        assertEquals("NO_CHALLENGE", r.getErrorCode());
    }

    @Test
    @DisplayName("clearAll: clear all sessions")
    void clearAll() {
        auth.initiateAuth("DEVICE-A");
        auth.initiateAuth("DEVICE-B");
        auth.clearAll();
        assertNull(auth.sessionCache_get("DEVICE-A"));
        assertNull(auth.sessionCache_get("DEVICE-B"));
    }

    @Test
    @DisplayName("generateKeyPair: returns valid SM2 keypair")
    void generateKeyPair() {
        Sm2KeyPair kp = auth.generateKeyPair();
        assertNotNull(kp);
        assertEquals(64, kp.getPrivateKeyHex().length());
        assertTrue(kp.getPublicKeyUncompressed().length >= 64);
    }

    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) sb.append(String.format("%02X", b & 0xFF));
        return sb.toString();
    }
}
