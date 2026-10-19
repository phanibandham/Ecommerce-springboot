package com.example.ecommerce.security;

import com.example.ecommerce.enums.Role;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtUtilTest {

    private static final String SECRET = "unit-test-secret-key-that-is-long-enough-123456";
    private static final String OTHER_SECRET = "a-completely-different-secret-key-for-testing-99";

    private final JwtUtil jwtUtil = new JwtUtil(SECRET, 3_600_000L);

    @Test
    void generatedToken_containsEmailAndRole() {
        String token = jwtUtil.generateToken("user@example.com", Role.USER);

        Claims claims = jwtUtil.extractClaims(token);

        assertThat(claims.getSubject()).isEqualTo("user@example.com");
        assertThat(claims.get("role", String.class)).isEqualTo("USER");
    }

    @Test
    void generatedToken_hasThreeParts() {
        String token = jwtUtil.generateToken("user@example.com", Role.USER);

        assertThat(token.split("\\.")).hasSize(3);
    }

    @Test
    void tokenSignedWithDifferentSecret_isRejected() {
        JwtUtil attacker = new JwtUtil(OTHER_SECRET, 3_600_000L);
        String forged = attacker.generateToken("hacker@example.com", Role.ADMIN);

        assertThatThrownBy(() -> jwtUtil.extractClaims(forged))
                .isInstanceOf(JwtException.class);
    }

    @Test
    void tokenWithBrokenSignature_isRejected() {
        String token = jwtUtil.generateToken("user@example.com", Role.USER);
        String broken = token.substring(0, token.lastIndexOf('.') + 1) + "brokensignature";

        assertThatThrownBy(() -> jwtUtil.extractClaims(broken))
                .isInstanceOf(JwtException.class);
    }

    @Test
    void expiredToken_isRejected() {
        JwtUtil shortLived = new JwtUtil(SECRET, -1000L);
        String token = shortLived.generateToken("user@example.com", Role.USER);

        assertThatThrownBy(() -> jwtUtil.extractClaims(token))
                .isInstanceOf(JwtException.class);
    }
}