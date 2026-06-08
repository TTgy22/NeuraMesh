package com.neuramesh.api.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * JWT 工具：HS256 签名，签发/校验 access 与 refresh 两类令牌。
 *
 * <p>密钥来自 {@code application.yml} 的 {@code neuramesh.jwt.secret}（赛后改为环境变量）。
 * accessToken 默认 15 分钟，refreshToken 默认 7 天。
 */
@Component
public class JwtUtil {

    /** 令牌类型声明键。 */
    public static final String CLAIM_TYPE = "typ";
    public static final String TYPE_ACCESS = "access";
    public static final String TYPE_REFRESH = "refresh";

    private static final long ACCESS_TTL_MS = 15 * 60 * 1000L;
    private static final long REFRESH_TTL_MS = 7 * 24 * 60 * 60 * 1000L;

    private final SecretKey key;

    public JwtUtil(@Value("${neuramesh.jwt.secret:neuramesh-demo-secret-key-please-change-in-production-0123456789}")
                   String secret) {
        // HS256 要求密钥 ≥ 256 bit；不足时右侧补齐
        byte[] raw = secret.getBytes(StandardCharsets.UTF_8);
        if (raw.length < 32) {
            byte[] padded = new byte[32];
            System.arraycopy(raw, 0, padded, 0, raw.length);
            for (int i = raw.length; i < 32; i++) {
                padded[i] = (byte) ('0' + (i % 10));
            }
            raw = padded;
        }
        this.key = Keys.hmacShaKeyFor(raw);
    }

    public String generateAccessToken(String userId, String username, String role, String address) {
        return build(userId, username, role, address, TYPE_ACCESS, ACCESS_TTL_MS);
    }

    public String generateRefreshToken(String userId, String username, String role, String address) {
        return build(userId, username, role, address, TYPE_REFRESH, REFRESH_TTL_MS);
    }

    private String build(String userId, String username, String role, String address,
                         String type, long ttlMs) {
        long now = System.currentTimeMillis();
        return Jwts.builder()
                .subject(userId)
                .claim("username", username)
                .claim("role", role)
                .claim("address", address)
                .claim(CLAIM_TYPE, type)
                .issuedAt(new Date(now))
                .expiration(new Date(now + ttlMs))
                .signWith(key)
                .compact();
    }

    /**
     * 解析并校验令牌，返回 claims；非法/过期抛出 {@link JwtException}。
     *
     * @param token JWT 字符串
     * @return claims
     */
    public Claims parse(String token) {
        return Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
    }

    /**
     * 从 claims 还原认证主体。
     *
     * @param claims 已校验 claims
     * @return 主体
     */
    public UserPrincipal toPrincipal(Claims claims) {
        return new UserPrincipal(
                claims.getSubject(),
                claims.get("username", String.class),
                claims.get("role", String.class),
                claims.get("address", String.class));
    }
}
