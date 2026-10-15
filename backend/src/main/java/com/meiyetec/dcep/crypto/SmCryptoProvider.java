package com.meiyetec.dcep.crypto;

import java.math.BigInteger;

/**
 * DCEP-03 国密算法适配层（SM2 / SM3 / SM4）
 *
 * <p>数字人民币（DCEP）使用国家商用密码算法（GM/T 系列）：
 * <ul>
 *   <li>SM2：椭圆曲线公钥密码（非对称签名 + 加密）</li>
 *   <li>SM3：杂凑算法（哈希，输出 256 bit）</li>
 *   <li>SM4：对称分组密码（128 bit 密钥 + 分组）</li>
 * </ul>
 *
 * <p>本接口提供软件实现，便于开发联调；生产环境可替换为基于国密硬件加密机（HSM）
 * 或智能密码钥匙（USB-Key）的实现，对调用方透明。
 *
 * <p>实现：{@link com.meiyetec.dcep.crypto.impl.BouncyCastleSmCryptoProvider}（基于 BouncyCastle）
 *
 * @see <a href="http://www.gmbz.org.cn/main/bzl.html">国家密码管理局标准</a>
 */
public interface SmCryptoProvider {

    // ============================================================
    // SM2：椭圆曲线非对称密码（签名 + 加密）
    // ============================================================

    /**
     * 生成 SM2 密钥对（256 bit 私钥）
     *
     * @return (privateKey, publicKey) — 私钥为 32 字节大整数，公钥为 64 字节未压缩点（04 || X || Y）
     */
    Sm2KeyPair generateSm2KeyPair();

    /**
     * SM2 签名
     *
     * @param privateKey 32 字节私钥大整数
     * @param publicKey  公钥点（用于 ZA 计算，标准 SM2 需要 userId + curve params）
     * @param plaintext  待签名原文
     * @return 签名值 (r, s) 均为 32 字节
     * @throws RuntimeException 包装 BouncyCastle 的 {@code CryptoException}
     */
    Sm2Signature sm2Sign(java.math.BigInteger privateKey, byte[] publicKey, byte[] plaintext);

    /**
     * SM2 验签
     * @throws RuntimeException 包装 BouncyCastle 的内部异常
     */
    boolean sm2Verify(byte[] publicKey, byte[] plaintext, Sm2Signature signature);

    /**
     * SM2 公钥加密（C1C3C2 模式，标准模式）
     *
     * @param publicKey 64 字节未压缩公钥点
     * @param plaintext 明文
     * @return 密文（C1C3C2 拼接）
     * @throws RuntimeException 包装 BouncyCastle 的 {@code InvalidCipherTextException}
     */
    byte[] sm2Encrypt(byte[] publicKey, byte[] plaintext);

    /**
     * SM2 公钥解密
     * @throws RuntimeException 包装 BouncyCastle 的 {@code InvalidCipherTextException}
     */
    byte[] sm2Decrypt(java.math.BigInteger privateKey, byte[] ciphertext);

    // ============================================================
    // SM3：杂凑算法（256 bit 输出）
    // ============================================================

    /**
     * SM3 哈希
     *
     * @return 32 字节（256 bit）哈希值
     */
    byte[] sm3Hash(byte[] input);

    /**
     * SM3 哈希（字符串便捷方法，UTF-8 编码）
     */
    String sm3HashHex(String input);

    // ============================================================
    // SM4：对称分组密码（128 bit 密钥 + 128 bit 分组，CBC + PKCS7Padding）
    // ============================================================

    /**
     * SM4 对称加密
     *
     * @param key       16 字节密钥
     * @param iv        16 字节初始向量（CBC 模式）
     * @param plaintext 明文
     * @return 密文（CBC + PKCS7Padding）
     */
    byte[] sm4Encrypt(byte[] key, byte[] iv, byte[] plaintext);

    /**
     * SM4 对称解密
     */
    byte[] sm4Decrypt(byte[] key, byte[] iv, byte[] ciphertext);

    /**
     * 当前 Provider 标识（"bouncycastle" / "hsm-vendor-x" 等）
     */
    String providerName();
}
