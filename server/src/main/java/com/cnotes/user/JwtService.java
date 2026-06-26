package com.cnotes.user;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Date;

/**
 * JWT 签发/校验。secret/expiry 来自 application.yml(密钥仅 env,默认值仅供本地 dev,>=256bit)。
 * subject=用户 id,附加 username claim;HS256。
 */
@Service
public class JwtService {

    @Value("${jwt.secret}")
    private String secret;

    @Value("${jwt.expiry:PT1h}")
    private String expiry;

    private SecretKey key;
    private Duration duration;

    @PostConstruct
    void init() {
        byte[] bytes = secret.getBytes(StandardCharsets.UTF_8);
        if (bytes.length < 32) {
            throw new IllegalStateException("jwt.secret 必须至少 32 字节(256bit)以支持 HS256");
        }
        this.key = Keys.hmacShaKeyFor(bytes);
        this.duration = Duration.parse(expiry);
    }

    public String generate(String userId, String username) {
        long now = System.currentTimeMillis();
        return Jwts.builder()
            .subject(userId)
            .claim("username", username)
            .issuedAt(new Date(now))
            .expiration(new Date(now + duration.toMillis()))
            .signWith(key)
            .compact();
    }

    /** 校验并返回用户 id;无效/过期返回 null(由 AuthFilter 决定放行/拒登)。 */
    public String parseUserId(String token) {
        try {
            return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload()
                .getSubject();
        } catch (Exception e) {
            return null;
        }
    }
}
