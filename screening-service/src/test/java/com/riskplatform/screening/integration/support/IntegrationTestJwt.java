package com.riskplatform.screening.integration.support;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;
import javax.crypto.SecretKey;

/** 集成测试 JWT 签发（密钥与 application.yml 默认一致）。 */
public final class IntegrationTestJwt {

    private static final String SECRET =
            "rdp-local-dev-secret-key-must-be-long-enough-256bit!!";

    private IntegrationTestJwt() {
    }

    public static String operatorToken() {
        return token("it-operator", List.of("OPERATOR"));
    }

    public static String token(String username, List<String> roles) {
        SecretKey key = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));
        Date now = new Date();
        return Jwts.builder()
                .subject(username)
                .claim("roles", roles)
                .issuedAt(now)
                .expiration(new Date(now.getTime() + 86400000L))
                .signWith(key)
                .compact();
    }
}
