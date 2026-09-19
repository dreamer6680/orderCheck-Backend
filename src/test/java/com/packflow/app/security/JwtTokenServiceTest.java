package com.packflow.app.security;

import com.packflow.app.user.Role;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtTokenServiceTest {

    private static final String SECRET = "test-only-jwt-signing-secret-that-is-at-least-32-bytes-long";

    @Test
    void createsConfiguredHmacTokenWithRequiredClaims() {
        JwtTokenService tokenService = new JwtTokenService(SECRET, 60_000);

        String token = tokenService.createToken("manager", Role.MANAGER);
        Claims claims = Jwts.parser()
                .verifyWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8)))
                .build()
                .parseSignedClaims(token)
                .getPayload();

        assertThat(claims.getSubject()).isEqualTo("manager");
        assertThat(claims.get("role", String.class)).isEqualTo("MANAGER");
        assertThat(claims.getIssuedAt()).isNotNull();
        assertThat(claims.getExpiration()).isNotNull();
        assertThat(Duration.between(claims.getIssuedAt().toInstant(), claims.getExpiration().toInstant()))
                .isEqualTo(Duration.ofMinutes(1));
    }

    @Test
    void rejectsTamperedTokens() {
        JwtTokenService tokenService = new JwtTokenService(SECRET, 60_000);

        assertThatThrownBy(() -> tokenService.parse(tokenService.createToken("sales", Role.SALES) + "tampered"))
                .isInstanceOf(JwtException.class);
    }

    @Test
    void rejectsExpiredTokens() {
        JwtTokenService tokenService = new JwtTokenService(SECRET, -1);

        assertThatThrownBy(() -> tokenService.parse(tokenService.createToken("sales", Role.SALES)))
                .isInstanceOf(JwtException.class);
    }
}
