package com.meiyetec.util;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

@Component
public class JwtUtil {

    /**
     * AUTH-005：refresh 允许的"过期宽限期"（毫秒）。
     * token 过期后 7 天内，前端可以拿旧 token 调 /auth/refresh 换取新 token。
     * 超过 7 天必须重新登录（防止永远不重登的安全风险）。
     */
    @Value("${jwt.refresh-grace-ms:604800000}")
    private Long refreshGraceMs;

    @Value("${jwt.secret}")
    private String secret;

    @Value("${jwt.expiration}")
    private Long expiration;

    /** 当前激活的 Spring profile，用于区分 dev / test / prod 环境 */
    @Value("${spring.profiles.active:}")
    private String activeProfiles;

    // 已通过启动校验的安全密钥（避免每次请求都重算）
    private volatile SecretKey validatedSigningKey;

    /**
     * S-1 · JWT 密钥强度校验（启动时执行）。
     *
     * 安全策略：
     *   - 长度 < 32 字节 → 任何 profile 下均启动失败（fail-fast）
     *   - 命中弱值模式（公司名 / dev-only / test-only 等）→ prod / 默认 profile 下启动失败，
     *     dev / test profile 下仅打印 WARN（允许本地开发不设真实密钥也能跑起来）
     *
     * 触发启动失败的弱值模式：
     *   - 包含 "meiyetec"（公司名）
     *   - 包含 "dev-only"（显式声明仅用于开发）
     *   - 包含 "test-only"（显式声明仅用于测试）
     *   - 包含 "not-for-production"（显式声明禁止生产）
     *
     * 生产部署必须设置环境变量 JWT_SECRET=<真实随机字符串>，
     * 同时确保 application.yml 的默认值不包含上述弱值标识。
     */
    private static final int MIN_SECRET_LENGTH = 32;

    /** 仅在 prod / 默认 profile 下触发启动失败的模式（dev/test 下 WARN 通过） */
    private static final List<Pattern> PROD_DENYLIST = List.of(
            Pattern.compile("(?i)meiyetec"),
            Pattern.compile("(?i)\\bdev-only\\b"),
            Pattern.compile("(?i)\\btest-only\\b"),
            Pattern.compile("(?i)not-for-production"),
            Pattern.compile("(?i)password[\\d]*"),
            Pattern.compile("(?i)123456")
    );

    @PostConstruct
    public void validateSecret() {
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException(
                    "[JwtUtil] jwt.secret 未配置！请设置环境变量 JWT_SECRET " +
                    "（生产必须使用 ≥ 32 字节的随机字符串）。");
        }

        if (secret.length() < MIN_SECRET_LENGTH) {
            // 长度不足：任何 profile 均 fail-fast
            throw new IllegalStateException(
                    "[JwtUtil] jwt.secret 太短（" + secret.length() + " 字节），" +
                    "必须 ≥ " + MIN_SECRET_LENGTH + " 字节。");
        }

        boolean isDevOrTest = Set.of(StringUtils.tokenizeToStringArray(activeProfiles, ","))
                .stream()
                .anyMatch(p -> p.equalsIgnoreCase("dev") || p.equalsIgnoreCase("test"));

        for (Pattern p : PROD_DENYLIST) {
            if (p.matcher(secret).find()) {
                String msg = "[JwtUtil] jwt.secret 命中弱值检测（pattern: " + p.pattern() + "）。" +
                             "生产/默认 profile 禁止使用此类密钥。请设置真实随机密钥到环境变量 JWT_SECRET。";
                if (isDevOrTest) {
                    // dev/test profile：仅 WARN，不阻断启动（允许本地开发不配密钥也能跑）
                    System.err.println("WARN  " + msg + "（dev/test profile，降级为 WARN 放行）");
                    return;
                }
                // prod 或默认 profile：fail-fast
                throw new IllegalStateException(msg);
            }
        }

        this.validatedSigningKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    private SecretKey getSigningKey() {
        if (validatedSigningKey != null) {
            return validatedSigningKey;
        }
        // 防御：PostConstruct 未触发时降级（理论上不会发生）
        return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * AUTH-005：单 ms 内重复调用 generateToken 仍能产生不同签名结果。
     * 通过把"纳秒级时间戳"写进 jti（token id）实现。
     */
    private long _seq = 0;

    public String generateToken(Long userId, String username) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("userId", userId);
        claims.put("username", username);
        claims.put("jti", System.nanoTime() + "-" + (++_seq));
        return createToken(claims, username);
    }

    private String createToken(Map<String, Object> claims, String subject) {
        return Jwts.builder()
                .claims(claims)
                .subject(subject)
                .issuedAt(new Date(System.currentTimeMillis()))
                .expiration(new Date(System.currentTimeMillis() + expiration))
                .signWith(getSigningKey())
                .compact();
    }

    public Claims extractAllClaims(String token) {
        return Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public String extractUsername(String token) {
        return extractAllClaims(token).getSubject();
    }

    public Long extractUserId(String token) {
        return extractAllClaims(token).get("userId", Long.class);
    }

    public boolean isTokenExpired(String token) {
        return extractAllClaims(token).getExpiration().before(new Date());
    }

    public boolean validateToken(String token, String username) {
        final String tokenUsername = extractUsername(token);
        return (tokenUsername.equals(username) && !isTokenExpired(token));
    }

    /**
     * AUTH-005：刷新 token。
     * 接受任意已签名（未被篡改）的旧 token，从中重新读取 userId+username 签发新 token。
     *
     * 过期宽限期：
     *  - 未过期 → 直接刷新
     *  - 已过期但在 refreshGraceMs（默认 7 天）内 → 仍可刷新
     *  - 超过宽限期 → 抛 JwtException
     *
     * 内部用 try/catch 处理 ExpiredJwtException，确保 signature 校验始终生效（防篡改）。
     */
    public String refresh(String oldToken) {
        if (oldToken == null || oldToken.isEmpty()) {
            throw new io.jsonwebtoken.JwtException("token is empty");
        }
        Claims claims;
        try {
            // 未过期：正常解析
            claims = Jwts.parser().verifyWith(getSigningKey()).build()
                    .parseSignedClaims(oldToken).getPayload();
        } catch (io.jsonwebtoken.ExpiredJwtException e) {
            // 已过期：在宽限期内仍可刷新
            Date exp = e.getClaims().getExpiration();
            long ageMs = System.currentTimeMillis() - exp.getTime();
            if (refreshGraceMs == null || ageMs > refreshGraceMs) {
                throw new io.jsonwebtoken.JwtException("token 超过刷新宽限期（" + refreshGraceMs + "ms），必须重新登录");
            }
            claims = e.getClaims();
        } catch (io.jsonwebtoken.JwtException e) {
            throw e;
        }

        Long uid = claims.get("userId", Long.class);
        String username = claims.getSubject();
        if (uid == null || username == null || username.isEmpty()) {
            throw new io.jsonwebtoken.JwtException("token 缺少必要字段");
        }
        return generateToken(uid, username);
    }
}
