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
            boolean useTls,
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
            String username,
            String password,
            int timeoutMs
    ) {
    }
}
