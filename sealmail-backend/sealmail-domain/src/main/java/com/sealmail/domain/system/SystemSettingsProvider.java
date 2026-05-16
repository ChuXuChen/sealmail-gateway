package com.sealmail.domain.system;

import java.time.Instant;
import java.util.List;

public interface SystemSettingsProvider {

    SystemSettingsSnapshot snapshot();

    record SystemSettingsSnapshot(
            RuntimeSettings runtime,
            SmtpServerSettings smtpServer,
            DeliverySettings delivery,
            CertificateValidationSettings certificateValidation,
            InternalCaSettings internalCa,
            List<CryptoCapability> cryptoCapabilities
    ) {
        public SystemSettingsSnapshot {
            cryptoCapabilities = cryptoCapabilities == null ? List.of() : List.copyOf(cryptoCapabilities);
        }
    }

    record RuntimeSettings(
            String applicationName,
            List<String> activeProfiles,
            boolean onlineEditingSupported,
            String configSource,
            Instant generatedAt
    ) {
        public RuntimeSettings {
            activeProfiles = activeProfiles == null ? List.of() : List.copyOf(activeProfiles);
        }
    }

    record SmtpServerSettings(
            String bindAddress,
            int port,
            int maxConnections,
            int maxMessageSizeBytes,
            TlsSettings tls
    ) {
    }

    record TlsSettings(
            boolean startTlsEnabled,
            boolean tlsRequired,
            boolean keystoreConfigured,
            boolean pemConfigured,
            String keyAlias
    ) {
    }

    record DeliverySettings(
            String mode,
            PostfixSettings postfix,
            RelaySettings directRelay
    ) {
    }

    record PostfixSettings(
            boolean enabled,
            String host,
            int afterFilterPort,
            int outboundPort,
            boolean useTls,
            int timeoutMs,
            String envelopeFrom
    ) {
    }

    record RelaySettings(
            String host,
            int port,
            boolean useTls,
            int timeoutMs,
            boolean usernameConfigured,
            boolean passwordConfigured,
            String username,
            String password
    ) {
        public RelaySettings redacted() {
            return new RelaySettings(host, port, useTls, timeoutMs, usernameConfigured, passwordConfigured, null, null);
        }
    }

    record CertificateValidationSettings(
            boolean crlEnabled,
            boolean ocspEnabled,
            int ocspTimeoutMs
    ) {
    }

    record InternalCaSettings(
            String crlBaseUrl,
            int defaultRootValidityDays,
            int defaultIntermediateValidityDays,
            int defaultEndEntityValidityDays
    ) {
    }

    record CryptoCapability(
            String category,
            List<String> algorithms
    ) {
        public CryptoCapability {
            algorithms = algorithms == null ? List.of() : List.copyOf(algorithms);
        }
    }
}
