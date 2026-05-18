package com.sealmail.app.dto.request;

public record RelayPolicyRequest(
        Boolean enabled,
        String host,
        Integer port,
        String username,
        String passwordSecretRef,
        Boolean clearPasswordSecretRef,
        Integer timeoutMs,
        String envelopeFrom,
        Boolean allowUnconfiguredExternalRecipientDomains
) {
}
