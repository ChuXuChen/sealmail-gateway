package com.sealmail.app.dto.request;

import com.sealmail.domain.mailsecurity.SmtpTransportSecurity;

public record RelayPolicyRequest(
        Boolean enabled,
        String host,
        Integer port,
        Boolean useTls,
        SmtpTransportSecurity transportSecurity,
        String username,
        String passwordSecretRef,
        Boolean clearPasswordSecretRef,
        Integer timeoutMs,
        String envelopeFrom
) {
}
