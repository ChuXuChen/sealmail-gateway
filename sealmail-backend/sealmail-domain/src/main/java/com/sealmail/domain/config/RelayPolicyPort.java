package com.sealmail.domain.config;

import java.time.Instant;

public interface RelayPolicyPort {

    RelayPolicySettings getSettings();

    RelayProbeSettings getProbeSettings();

    RelayPolicySettings updateSettings(RelayPolicySettingsUpdate update);

    record RelayPolicySettings(
            boolean enabled,
            String host,
            int port,
            String username,
            boolean passwordConfigured,
            String passwordSecretRef,
            int timeoutMs,
            String envelopeFrom,
            boolean allowUnconfiguredExternalRecipientDomains,
            Instant updatedAt
    ) {
    }

    record RelayPolicySettingsUpdate(
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

    record RelayProbeSettings(
            boolean enabled,
            String host,
            int port,
            String username,
            String password,
            int timeoutMs
    ) {
    }
}
