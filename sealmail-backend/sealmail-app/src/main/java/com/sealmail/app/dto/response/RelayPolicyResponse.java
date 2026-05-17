package com.sealmail.app.dto.response;

import com.sealmail.domain.mailsecurity.SmtpTransportSecurity;

import java.time.Instant;

public record RelayPolicyResponse(
        boolean enabled,
        String host,
        int port,
        boolean useTls,
        SmtpTransportSecurity transportSecurity,
        String username,
        boolean passwordConfigured,
        String passwordSecretRef,
        int timeoutMs,
        String envelopeFrom,
        Instant updatedAt
) {
}
