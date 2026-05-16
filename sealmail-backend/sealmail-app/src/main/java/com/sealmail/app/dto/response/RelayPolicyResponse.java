package com.sealmail.app.dto.response;

import java.time.Instant;

public record RelayPolicyResponse(
        boolean enabled,
        String host,
        int port,
        boolean useTls,
        String username,
        boolean passwordConfigured,
        String passwordSecretRef,
        int timeoutMs,
        String envelopeFrom,
        Instant updatedAt
) {
}
