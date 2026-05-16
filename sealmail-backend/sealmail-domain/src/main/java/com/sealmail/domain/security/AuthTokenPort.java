package com.sealmail.domain.security;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface AuthTokenPort {

    IssuedToken issue(TokenSubject subject);

    Optional<TokenSubject> read(String token);

    record TokenSubject(
            String userId,
            String username,
            String email,
            List<String> roles,
            List<String> managedDomains,
            Instant issuedAt
    ) {
    }

    record IssuedToken(
            String value,
            long expiresInSeconds
    ) {
    }
}
