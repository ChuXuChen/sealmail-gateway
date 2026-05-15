package com.sealmail.web.security;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class JwtTokenProviderTest {

    @Test
    void exposesMillisecondIssuedAtForTokenInvalidationComparison() throws Exception {
        JwtTokenProvider provider = new JwtTokenProvider(
                "sealmail-super-secret-key-for-jwt-token-signing-2026",
                86400
        );

        long beforeMillis = System.currentTimeMillis();
        String token = provider.createToken(
                "user-1",
                "superadmin",
                "superadmin@sealmail.local",
                List.of("SUPER_ADMIN"),
                List.of()
        );
        long afterMillis = System.currentTimeMillis();

        Instant issuedAt = provider.getIssuedAt(token);

        assertNotNull(issuedAt);
        assertFalse(issuedAt.isBefore(Instant.ofEpochMilli(beforeMillis)));
        assertFalse(issuedAt.isAfter(Instant.ofEpochMilli(afterMillis)));
    }
}
