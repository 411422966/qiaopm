package com.meiyetec.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * 灰度发布 / A/B 实验稳定哈希工具。
 * 从原 GrayReleaseService.stableHash 抽出（I121）。
 */
public final class GrayReleaseHasher {

    private GrayReleaseHasher() {}

    public static long stableHash(String s) {
        try {
            byte[] b = MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8));
            long h = 0;
            for (int i = 0; i < 8; i++) h = (h << 8) | (b[i] & 0xFF);
            return h;
        } catch (Exception e) {
            return s.hashCode();
        }
    }
}
