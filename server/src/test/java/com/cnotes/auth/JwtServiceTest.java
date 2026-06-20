package com.cnotes.auth;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class JwtServiceTest {

    private final JwtService jwt = new JwtService("test-secret-test-secret-test-secret");

    @Test
    void issuedTokenVerifiesBackToSubject() {
        String token = jwt.issue("user-123", Duration.ofMinutes(5));
        assertThat(jwt.verify(token)).contains("user-123");
    }

    @Test
    void tamperedTokenRejected() {
        String token = jwt.issue("user-123", Duration.ofMinutes(5));
        assertThat(jwt.verify(token + "x")).isEmpty();
        assertThat(jwt.verify("a.b.c")).isEmpty();
        assertThat(jwt.verify(null)).isEmpty();
        assertThat(jwt.verify("")).isEmpty();
    }

    @Test
    void wrongSecretRejected() {
        String token = jwt.issue("u", Duration.ofMinutes(5));
        JwtService other = new JwtService("a-totally-different-secret-value!!");
        assertThat(other.verify(token)).isEmpty();
    }

    @Test
    void expiredTokenRejected() {
        String token = jwt.issue("u", Duration.ofSeconds(-1));
        Optional<String> r = jwt.verify(token);
        assertThat(r).isEmpty();
    }
}
