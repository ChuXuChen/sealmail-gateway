package com.sealmail.domain.config;

import com.sealmail.domain.mailsecurity.SmtpTransportSecurity;

import java.time.Instant;

public interface RelayPolicyPort {

    RelayPolicySettings getSettings();

    RelayProbeSettings getProbeSettings();

    RelayPolicySettings updateSettings(RelayPolicySettingsUpdate update);

    record RelayPolicySettings(
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

    record RelayPolicySettingsUpdate(
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

    record RelayProbeSettings(
            boolean enabled,
            String host,
            int port,
            boolean useTls,
            SmtpTransportSecurity transportSecurity,
            String username,
            String password,
            int timeoutMs
    ) {
    }
}
