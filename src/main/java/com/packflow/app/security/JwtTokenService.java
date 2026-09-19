package com.packflow.app.security;

import com.packflow.app.user.AppUser;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class JwtTokenService {

    private final SecretKey signingKey;
    private final long expirationMillis;

    public JwtTokenService(
            @Value("${app.security.jwt-secret}") String jwtSecret,
            @Value("${app.security.jwt-expiration}") long expirationMillis) {
        this.signingKey = Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
        this.expirationMillis = expirationMillis;
    }

    public String createToken(AppUser user) {
        return createToken(user.getUsername(), user.getRole(), user.getTokenVersion());
    }

    private String createToken(String username, com.packflow.app.user.Role role, long tokenVersion) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(username)
                .claim("role", role.name())
                .claim("tokenVersion", tokenVersion)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusMillis(expirationMillis)))
                .signWith(signingKey)
                .compact();
    }

    public JwtPrincipal parse(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(signingKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
        Object version = claims.get("tokenVersion");
        if (!(version instanceof Number number)) {
            throw new IllegalArgumentException("Token version missing");
        }
        return new JwtPrincipal(claims.getSubject(), Role.valueOf(claims.get("role", String.class)),
                number.longValue());
    }
}
