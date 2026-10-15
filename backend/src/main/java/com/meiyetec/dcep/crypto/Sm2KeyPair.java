package com.meiyetec.dcep.crypto;

import java.math.BigInteger;

/**
 * DCEP-03：SM2 密钥对（私钥 + 公钥点）
 *
 * <p>SM2 使用 256 bit 素域上的椭圆曲线，私钥为 32 字节大整数，
 * 公钥为 64 字节未压缩点（04 || X || Y）。
 */
public class Sm2KeyPair {
    private final BigInteger privateKey;
    private final byte[] publicKeyUncompressed;

    public Sm2KeyPair(BigInteger privateKey, byte[] publicKeyUncompressed) {
        this.privateKey = privateKey;
        this.publicKeyUncompressed = publicKeyUncompressed;
    }

    public BigInteger getPrivateKey() {
        return privateKey;
    }

    /** 64 字节 X || Y（无 04 前缀），用于业务序列化 */
    public byte[] getPublicKeyUncompressed() {
        return publicKeyUncompressed;
    }

    /** 65 字节 04 || X || Y 标准未压缩格式，用于 BouncyCastle ECPoint 解码 */
    public byte[] getPublicKeyWithPrefix() {
        if (publicKeyUncompressed.length == 65) {
            return publicKeyUncompressed;
        }
        byte[] out = new byte[65];
        out[0] = 0x04;
        System.arraycopy(publicKeyUncompressed, 0, out, 1, 64);
        return out;
    }

    /** 十六进制字符串（含大写字母） */
    public String getPrivateKeyHex() {
        String hex = privateKey.toString(16);
        // 补齐 64 字符
        StringBuilder sb = new StringBuilder(64);
        for (int i = hex.length(); i < 64; i++) sb.append('0');
        sb.append(hex);
        return sb.toString().toUpperCase();
    }

    public String getPublicKeyHex() {
        StringBuilder sb = new StringBuilder(publicKeyUncompressed.length * 2);
        for (byte b : publicKeyUncompressed) {
            sb.append(String.format("%02X", b & 0xff));
        }
        return sb.toString();
    }
}
