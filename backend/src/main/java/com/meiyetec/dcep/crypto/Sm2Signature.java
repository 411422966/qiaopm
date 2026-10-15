package com.meiyetec.dcep.crypto;

import java.math.BigInteger;

/**
 * DCEP-03：SM2 签名值 (r, s)
 */
public class Sm2Signature {
    private final BigInteger r;
    private final BigInteger s;

    public Sm2Signature(BigInteger r, BigInteger s) {
        this.r = r;
        this.s = s;
    }

    public BigInteger getR() {
        return r;
    }

    public BigInteger getS() {
        return s;
    }

    /** ASN.1 DER 编码（r || s 各 32 字节） */
    public byte[] toBytes() {
        byte[] rBytes = toFixedLength(r.toByteArray(), 32);
        byte[] sBytes = toFixedLength(s.toByteArray(), 32);
        byte[] out = new byte[64];
        System.arraycopy(rBytes, 0, out, 0, 32);
        System.arraycopy(sBytes, 0, out, 32, 32);
        return out;
    }

    /** 十六进制字符串（128 字符） */
    public String toHex() {
        byte[] bytes = toBytes();
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format("%02X", b & 0xff));
        }
        return sb.toString();
    }

    public static Sm2Signature fromBytes(byte[] r, byte[] s) {
        return new Sm2Signature(new BigInteger(1, r), new BigInteger(1, s));
    }

    /** 从 64 字节构造 */
    public static Sm2Signature fromConcat(byte[] rPlusS) {
        if (rPlusS.length != 64) {
            throw new IllegalArgumentException("SM2 签名必须为 64 字节，实际=" + rPlusS.length);
        }
        byte[] r = new byte[32];
        byte[] s = new byte[32];
        System.arraycopy(rPlusS, 0, r, 0, 32);
        System.arraycopy(rPlusS, 32, s, 0, 32);
        return fromBytes(r, s);
    }

    private static byte[] toFixedLength(byte[] src, int length) {
        if (src.length == length) return src;
        byte[] out = new byte[length];
        if (src.length > length) {
            // 截掉前导 0 字节
            System.arraycopy(src, src.length - length, out, 0, length);
        } else {
            // 前补 0
            System.arraycopy(src, 0, out, length - src.length, src.length);
        }
        return out;
    }
}
