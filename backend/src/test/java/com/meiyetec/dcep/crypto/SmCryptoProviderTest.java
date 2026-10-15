package com.meiyetec.dcep.crypto;

import com.meiyetec.dcep.crypto.impl.BouncyCastleSmCryptoProvider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * DCEP-03 国密算法单元测试
 *
 * <p>基于 BouncyCastle 软件实现，覆盖：
 * <ol>
 *   <li>SM2 密钥对生成 / 签名 / 验签</li>
 *   <li>SM2 加密 / 解密</li>
 *   <li>SM3 哈希（输出长度 + hex 格式）</li>
 *   <li>SM4 对称加解密（CBC + PKCS7Padding）</li>
 *   <li>与已知 SM3 测试向量对比</li>
 * </ol>
 */
@DisplayName("DCEP-03 国密算法 SM2/SM3/SM4")
class SmCryptoProviderTest {

    private final SmCryptoProvider crypto = new BouncyCastleSmCryptoProvider();

    @Test
    @DisplayName("SM3: 空字符串哈希应为标准测试向量")
    void sm3_knownVector_emptyString() {
        // GM/T 0004-2012 附录 A.1 标准测试向量：
        // SM3("") = 1ab21d8355cfa17f8e61194831e81a8f22bec8c728fefb747ed035eb5082aa2b
        String empty = "";
        String hash = crypto.sm3HashHex(empty);
        // SM3("") 标准值（GM/T 0004-2012 附录 A.1）：
        assertEquals(64, hash.length(), "SM3 输出应为 64 hex 字符（256 bit）");
        assertEquals("1AB21D8355CFA17F8E61194831E81A8F22BEC8C728FEFB747ED035EB5082AA2B",
                hash.toUpperCase(),
                "SM3(\"\") 应匹配 GM/T 0004-2012 标准测试向量");
    }

    @Test
    @DisplayName("SM3: 'abc' 应匹配标准测试向量")
    void sm3_knownVector_abc() {
        // GM/T 0004-2012 附录 A.2 标准测试向量：
        // SM3("abc") = 66c7f0f462eeedd9d1f2d46bdc10e4e24167c4875cf2f7a2297da02b8f4ba8e0
        String hash = crypto.sm3HashHex("abc");
        assertEquals("66C7F0F462EEEDD9D1F2D46BDC10E4E24167C4875CF2F7A2297DA02B8F4BA8E0",
                hash.toUpperCase(),
                "SM3(\"abc\") 应匹配 GM/T 0004-2012 标准测试向量");
    }

    @Test
    @DisplayName("SM3: 同一输入 → 同一哈希；不同输入 → 不同哈希")
    void sm3_deterministic() {
        String h1 = crypto.sm3HashHex("hello");
        String h2 = crypto.sm3HashHex("hello");
        String h3 = crypto.sm3HashHex("world");
        assertEquals(h1, h2);
        assertNotEquals(h1, h3);
    }

    @Test
    @DisplayName("SM3: 输出始终 64 hex 字符")
    void sm3_outputLength() {
        for (String input : new String[]{"", "a", "中文测试", "1234567890ABCDEF"}) {
            String hash = crypto.sm3HashHex(input);
            assertEquals(64, hash.length(), "SM3 输出长度对 input='" + input + "' 应为 64");
            assertTrue(hash.matches("[0-9A-F]{64}"), "SM3 输出必须是 16 进制大写");
        }
    }

    @Test
    @DisplayName("SM2: 生成的密钥对私钥 32 字节大整数；公钥 64 字节未压缩点")
    void sm2_generateKeyPair() {
        Sm2KeyPair kp1 = crypto.generateSm2KeyPair();
        Sm2KeyPair kp2 = crypto.generateSm2KeyPair();

        // 私钥 32 字节
        assertEquals(64, kp1.getPrivateKeyHex().length(), "私钥 hex 应为 64 字符");
        assertTrue(kp1.getPrivateKey().compareTo(BigInteger.ONE) > 0);

        // 公钥 64 字节（无 04 前缀）或 65 字节（含 04）
        assertTrue(kp2.getPublicKeyUncompressed().length == 64
                        || kp2.getPublicKeyUncompressed().length == 65,
                "公钥未压缩点应为 64 或 65 字节");

        // 两次生成的密钥对必须不同
        assertNotEquals(kp1.getPrivateKey(), kp2.getPrivateKey());
    }

    @Test
    @DisplayName("SM2: sign + verify 完整闭环")
    void sm2_signVerifyRoundtrip() {
        Sm2KeyPair kp = crypto.generateSm2KeyPair();
        byte[] plaintext = "美业SaaS DCEP 测试".getBytes(StandardCharsets.UTF_8);

        Sm2Signature sig = crypto.sm2Sign(kp.getPrivateKey(), kp.getPublicKeyUncompressed(), plaintext);
        assertNotNull(sig);
        assertNotNull(sig.getR());
        assertNotNull(sig.getS());
        // r 和 s 必须在 [1, n-1] 区间
        assertTrue(sig.getR().signum() > 0, "r 必须 > 0");
        assertTrue(sig.getS().signum() > 0, "s 必须 > 0");

        boolean ok = crypto.sm2Verify(kp.getPublicKeyUncompressed(), plaintext, sig);
        assertTrue(ok, "SM2 验签必须通过");

        // 篡改明文后验签失败
        boolean tampered = crypto.sm2Verify(
                kp.getPublicKeyUncompressed(),
                "美业SaaS DCEP 篡改".getBytes(StandardCharsets.UTF_8),
                sig);
        assertFalse(tampered, "明文被篡改后验签必须失败");
    }

    @Test
    @DisplayName("SM2: 100 次 sign+verify 全部成功（健壮性）")
    void sm2_signVerifyStability() {
        Sm2KeyPair kp = crypto.generateSm2KeyPair();
        Set<String> sigHexes = new HashSet<>();
        for (int i = 0; i < 100; i++) {
            byte[] pt = ("tx-" + i).getBytes(StandardCharsets.UTF_8);
            Sm2Signature sig = crypto.sm2Sign(kp.getPrivateKey(), kp.getPublicKeyUncompressed(), pt);
            assertTrue(crypto.sm2Verify(kp.getPublicKeyUncompressed(), pt, sig));
            sigHexes.add(sig.toHex());
        }
        assertEquals(100, sigHexes.size(), "100 次签名结果应全部不同");
    }

    @Test
    @DisplayName("SM2: encrypt + decrypt 完整闭环")
    void sm2_encryptDecryptRoundtrip() {
        Sm2KeyPair kp = crypto.generateSm2KeyPair();
        byte[] plaintext = "美业DCEP 加密测试 12345".getBytes(StandardCharsets.UTF_8);

        byte[] ciphertext = crypto.sm2Encrypt(kp.getPublicKeyUncompressed(), plaintext);
        assertNotNull(ciphertext);
        // SM2 密文长度 ≥ 原文长度（包含 C1 C3 C2）
        assertTrue(ciphertext.length >= plaintext.length);

        byte[] decrypted = crypto.sm2Decrypt(kp.getPrivateKey(), ciphertext);
        assertArrayEquals(plaintext, decrypted, "SM2 解密后必须等于原文");
    }

    @Test
    @DisplayName("SM2: toHex 格式正确（128 字符大写 hex）")
    void sm2_signatureToHex() {
        Sm2KeyPair kp = crypto.generateSm2KeyPair();
        Sm2Signature sig = crypto.sm2Sign(kp.getPrivateKey(), kp.getPublicKeyUncompressed(), "test".getBytes());
        String hex = sig.toHex();
        assertEquals(128, hex.length(), "SM2 签名 hex 应为 128 字符（64 字节）");
        assertTrue(hex.matches("[0-9A-F]{128}"), "签名 hex 必须是 16 进制大写");
    }

    @Test
    @DisplayName("SM4: encrypt + decrypt 完整闭环")
    void sm4_encryptDecryptRoundtrip() {
        byte[] key = "0123456789ABCDEF".getBytes(StandardCharsets.UTF_8);
        byte[] iv = "FEDCBA9876543210".getBytes(StandardCharsets.UTF_8);
        byte[] plaintext = "美业DCEP SM4 加密测试 12345".getBytes(StandardCharsets.UTF_8);

        byte[] ciphertext = crypto.sm4Encrypt(key, iv, plaintext);
        assertNotNull(ciphertext);
        // CBC + PKCS7Padding：密文长度 = ceil(plaintext.length / 16) * 16
        int expectedLen = ((plaintext.length / 16) + 1) * 16;
        assertEquals(expectedLen, ciphertext.length, "SM4 CBC 密文长度 = ceil(原文/16)*16");

        byte[] decrypted = crypto.sm4Decrypt(key, iv, ciphertext);
        assertArrayEquals(plaintext, decrypted, "SM4 解密后必须等于原文");
    }

    @Test
    @DisplayName("SM4: key 长度非法必须抛 IllegalArgumentException")
    void sm4_invalidKeyLength() {
        byte[] badKey = "短key".getBytes(StandardCharsets.UTF_8);  // 仅 6 字节
        byte[] iv = "0123456789ABCDEF".getBytes(StandardCharsets.UTF_8);
        assertThrows(IllegalArgumentException.class,
                () -> crypto.sm4Encrypt(badKey, iv, "data".getBytes()));
    }

    @Test
    @DisplayName("SM4: IV 长度非法必须抛 IllegalArgumentException")
    void sm4_invalidIvLength() {
        byte[] key = "0123456789ABCDEF".getBytes(StandardCharsets.UTF_8);
        byte[] badIv = "短".getBytes(StandardCharsets.UTF_8);  // 仅 3 字节
        assertThrows(IllegalArgumentException.class,
                () -> crypto.sm4Encrypt(key, badIv, "data".getBytes()));
    }

    @Test
    @DisplayName("Provider 名称应包含 bouncycastle")
    void providerName() {
        assertTrue(crypto.providerName().toLowerCase().contains("bouncycastle"));
    }
}
