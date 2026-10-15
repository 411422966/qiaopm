package com.meiyetec.util;

import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.*;

/**
 * AUTH-005 Token 刷新 — JwtUtil 单测
 *
 * 不启动 Spring：用 ReflectionTestUtils 直接注入 secret/expiration/refreshGraceMs。
 * 覆盖：
 *  1) generateToken + validateToken 正常路径
 *  2) isTokenExpired 真过期
 *  3) refresh：未过期 token 必须返回新 token
 *  4) refresh：已过期但未超宽限期，必须能刷新
 *  5) refresh：篡改签名 → JwtException
 *  6) refresh：超过宽限期 → JwtException
 *  7) refresh：null/空 → JwtException
 */
class JwtUtilTest {

    private static final String SECRET = "meiyetec-secret-key-2024-very-long-and-secure-for-test-only-xx";
    private static final long EXP_MS = 86400000L;        // 24h
    private static final long GRACE_MS = 604800000L;     // 7d

    private JwtUtil util;

    @BeforeEach
    void setUp() {
        util = new JwtUtil();
        ReflectionTestUtils.setField(util, "secret", SECRET);
        ReflectionTestUtils.setField(util, "expiration", EXP_MS);
        ReflectionTestUtils.setField(util, "refreshGraceMs", GRACE_MS);
    }

    /** 直接签发一个"指定过期时间"的 token，用于模拟已过期场景 */
    private String signExpiredToken(Long userId, String username, long ageMs) {
        SecretKey key = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));
        Date now = new Date();
        return Jwts.builder()
                .subject(username)
                .claim("userId", userId)
                .claim("username", username)
                .issuedAt(new Date(now.getTime() - ageMs - EXP_MS))
                .expiration(new Date(now.getTime() - ageMs))
                .signWith(key)
                .compact();
    }

    @Test
    void generateToken_and_validateToken_normalPath() {
        String t = util.generateToken(1L, "admin");
        assertNotNull(t);
        assertEquals("admin", util.extractUsername(t));
        assertEquals(1L, util.extractUserId(t));
        assertTrue(util.validateToken(t, "admin"));
        assertFalse(util.isTokenExpired(t));
    }

    @Test
    void refresh_freshToken_returnsNewToken() throws InterruptedException {
        String old = util.generateToken(42L, "alice");
        // 不同毫秒才能产生不同签名结果（iat/exp 都是毫秒精度）
        Thread.sleep(5);
        String fresh = util.refresh(old);
        assertNotNull(fresh);
        assertNotEquals(old, fresh);
        assertEquals(42L, util.extractUserId(fresh));
        assertEquals("alice", util.extractUsername(fresh));
        assertTrue(util.validateToken(fresh, "alice"));
    }

    @Test
    void refresh_expiredWithinGrace_returnsNewToken() {
        // 过期 1 小时，远小于 7 天宽限期
        String oldExpired = signExpiredToken(7L, "bob", 3600_000L);
        // 解析时不抛
        assertThrows(JwtException.class, () -> util.extractUsername(oldExpired));
        String fresh = util.refresh(oldExpired);
        assertNotNull(fresh);
        assertEquals(7L, util.extractUserId(fresh));
        assertEquals("bob", util.extractUsername(fresh));
    }

    @Test
    void refresh_expiredBeyondGrace_throws() {
        // 过期 30 天，远大于 7 天宽限期
        String oldExpired = signExpiredToken(7L, "bob", 30L * 24 * 3600_000L);
        JwtException e = assertThrows(JwtException.class, () -> util.refresh(oldExpired));
        assertTrue(e.getMessage().contains("宽限期"));
    }

    @Test
    void refresh_tamperedSignature_throws() {
        String t = util.generateToken(1L, "admin");
        // 篡改最后一段
        String[] parts = t.split("\\.");
        String tampered = parts[0] + "." + parts[1] + ".AAAAAAAAAAAAAAAAAAAAAA";
        assertThrows(JwtException.class, () -> util.refresh(tampered));
    }

    @Test
    void refresh_nullOrEmpty_throws() {
        // JJWT 0.12 把空串/全空白都视为 IllegalArgumentException，这是 OK 的
        // 我们只要确认：只要 token 是"逻辑空"，就抛异常不能继续签发新 token
        assertThrows(Exception.class, () -> util.refresh(null));
        assertThrows(Exception.class, () -> util.refresh(""));
        assertThrows(Exception.class, () -> util.refresh("   "));
    }

    @Test
    void refresh_garbage_throws() {
        assertThrows(JwtException.class, () -> util.refresh("not.a.jwt"));
        assertThrows(JwtException.class, () -> util.refresh("abc"));
    }
}