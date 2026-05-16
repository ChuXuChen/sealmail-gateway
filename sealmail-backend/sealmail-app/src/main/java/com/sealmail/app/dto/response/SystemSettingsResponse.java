package com.sealmail.app.dto.response;

import java.time.Instant;
import java.util.List;

public record SystemSettingsResponse(
        RuntimeResponse runtime,
        SmtpServerResponse smtpServer,
        DeliveryResponse delivery,
        QuarantinePolicyResponse quarantinePolicy,
        CertificateValidationResponse certificateValidation,
        InternalCaResponse internalCa,
        List<CryptoCapabilityResponse> cryptoCapabilities
) {
    public SystemSettingsResponse {
        cryptoCapabilities = cryptoCapabilities == null ? List.of() : List.copyOf(cryptoCapabilities);
    }

    public record RuntimeResponse(
            String applicationName,
            List<String> activeProfiles,
            boolean onlineEditingSupported,
            String configSource,
            Instant generatedAt
    ) {
        public RuntimeResponse {
            activeProfiles = activeProfiles == null ? List.of() : List.copyOf(activeProfiles);
        }
    }

    public record SmtpServerResponse(
            String bindAddress,
            int port,
            int maxConnections,
            int maxMessageSizeBytes,
            TlsResponse tls
    ) {
    }

    public record TlsResponse(
            boolean startTlsEnabled,
            boolean tlsRequired,
            boolean keystoreConfigured,
            boolean pemConfigured,
            String keyAlias
    ) {
    }

    public record DeliveryResponse(
            String mode,
            PostfixResponse postfix,
            RelayResponse directRelay
    ) {
    }

    public record PostfixResponse(
            boolean enabled,
            String host,
            int afterFilterPort,
            int outboundPort,
            boolean useTls,
            int timeoutMs,
            String envelopeFrom
    ) {
    }

    public record RelayResponse(
            String host,
            int port,
            boolean useTls,
            int timeoutMs,
            boolean usernameConfigured,
            boolean passwordConfigured
    ) {
    }

    public record QuarantinePolicyResponse(
            int maxRetentionDays,
            boolean notificationEnabled,
            boolean releaseRequiresEncryption
    ) {
    }

    public record CertificateValidationResponse(
            boolean crlEnabled,
            boolean ocspEnabled,
            int ocspTimeoutMs
    ) {
    }

    public record InternalCaResponse(
            String crlBaseUrl,
            int defaultRootValidityDays,
            int defaultIntermediateValidityDays,
            int defaultEndEntityValidityDays
    ) {
    }

    public record CryptoCapabilityResponse(
            String category,
            List<String> algorithms
    ) {
        public CryptoCapabilityResponse {
            algorithms = algorithms == null ? List.of() : List.copyOf(algorithms);
        }
    }
}
