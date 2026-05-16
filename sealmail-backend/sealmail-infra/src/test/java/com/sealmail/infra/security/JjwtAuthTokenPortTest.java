package com.sealmail.infra.security;

import com.sealmail.domain.security.AuthTokenPort;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JjwtAuthTokenPortTest {

    @Test
    void tokenRoundTripsUserClaims() {
        JjwtAuthTokenPort port = new JjwtAuthTokenPort(
                secretRef -> "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef",
                "env:JWT_SECRET",
                3600);

        AuthTokenPort.IssuedToken issued = port.issue(new AuthTokenPort.TokenSubject(
                "u-1",
                "alice",
                "alice@example.com",
                List.of("PKI_ADMIN"),
                List.of("example.com"),
                Instant.now()));

        AuthTokenPort.TokenSubject subject = port.read(issued.value()).orElseThrow();

        assertEquals(3600, issued.expiresInSeconds());
        assertEquals("u-1", subject.userId());
        assertEquals("alice", subject.username());
        assertEquals("alice@example.com", subject.email());
        assertTrue(subject.roles().contains("PKI_ADMIN"));
        assertTrue(subject.managedDomains().contains("EXAMPLE.COM"));
    }
}
