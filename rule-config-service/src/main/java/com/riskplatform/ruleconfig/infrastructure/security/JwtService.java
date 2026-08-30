package com.riskplatform.ruleconfig.infrastructure.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;

/**
 * JWT 签发与校验（S10）。HS256 + 配置密钥，载荷含 username 与 roles。
 */
@Component
public class JwtService {

    private final SecretKey key;
    private final long expireMillis;

    public JwtService(
            @Value("${security.jwt.secret:rdp-local-dev-secret-key-must-be-long-enough-256bit!!}") String secret,
            @Value("${security.jwt.expire-ms:86400000}") long expireMillis) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expireMillis = expireMillis;
    }

    /** 签发 token。 */
    public String issue(String username, List<String> roles) {
        Date now = new Date();
        return Jwts.builder()
                .subject(username)
                .claim("roles", roles)
                .issuedAt(now)
                .expiration(new Date(now.getTime() + expireMillis))
                .signWith(key)
                .compact();
    }

    /** 解析校验 token，返回 Claims；无效/过期抛异常。 */
    public Claims parse(String token) {
        return Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
    }
}
