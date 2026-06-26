package com.takeout.common.util;

import com.takeout.common.enums.UserRole;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Map;

@Slf4j
public final class JwtUtil {

    private JwtUtil() {}

    private static final String CLAIM_USER_ID = "userId";
    private static final String CLAIM_USER_ROLE = "userRole";

    public static String generateToken(Long userId, UserRole userRole, String secret, long expireMs) {
        SecretKey key = buildKey(secret);
        long now = System.currentTimeMillis();
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claims(Map.of(CLAIM_USER_ID, userId, CLAIM_USER_ROLE, userRole.name()))
                .issuedAt(new Date(now))
                .expiration(new Date(now + expireMs))
                .signWith(key)
                .compact();
    }

    public static Claims parseToken(String token, String secret) {
        try {
            return Jwts.parser()
                    .verifyWith(buildKey(secret))
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (JwtException | IllegalArgumentException e) {
            log.warn("JWT 解析失败: {}", e.getMessage());
            return null;
        }
    }

    public static Long getUserId(String token, String secret) {
        Claims claims = parseToken(token, secret);
        if (claims == null) return null;
        Object userId = claims.get(CLAIM_USER_ID);
        if (userId instanceof Integer i) return i.longValue();
        if (userId instanceof Long l) return l;
        return null;
    }

    public static UserRole getUserRole(String token, String secret) {
        Claims claims = parseToken(token, secret);
        if (claims == null) return null;
        String roleStr = claims.get(CLAIM_USER_ROLE, String.class);
        if (roleStr == null) return null;
        try {
            return UserRole.valueOf(roleStr);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static SecretKey buildKey(String secret) {
        byte[] keyBytes = secret.getBytes(StandardCharsets.UTF_8);
        if (keyBytes.length < 32) {
            throw new IllegalArgumentException(
                    "JWT secret 长度不足，至少需要 32 字节，当前: " + keyBytes.length + " 字节");
        }
        return Keys.hmacShaKeyFor(keyBytes);
    }
}
