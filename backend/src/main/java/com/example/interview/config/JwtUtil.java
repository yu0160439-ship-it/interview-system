package com.example.interview.config;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

/**
 * JWT 工具：签发与解析访问令牌。
 * 兼容模式：若请求带 Bearer token 则用于身份识别；不带 token 的请求仍允许（身份从请求参数读取），
 * 以便旧前端与公开接口可以直接调用。
 */
@Component
public class JwtUtil {

    @Value("${app.jwt.secret}")
    private String secret;

    @Value("${app.jwt.expire-hours}")
    private int expireHours;

    private SecretKey key() {
        return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    public String generate(String name, String role, String groupName, boolean isAdmin) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + expireHours * 3600_000L);
        return Jwts.builder()
                .subject(name)
                .claim("role", role)
                .claim("groupName", groupName == null ? "" : groupName)
                .claim("isAdmin", isAdmin)
                .issuedAt(now)
                .expiration(expiry)
                .signWith(key())
                .compact();
    }

    /**
     * 解析 token，返回 payload；无效或过期返回 null。
     */
    public Claims parse(String token) {
        try {
            return Jwts.parser()
                    .verifyWith(key())
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (Exception e) {
            return null;
        }
    }
}
