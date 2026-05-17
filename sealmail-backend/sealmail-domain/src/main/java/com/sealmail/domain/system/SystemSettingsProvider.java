package com.sealmail.domain.system;

import com.sealmail.domain.mailsecurity.SmtpTransportSecurity;

import java.time.Instant;
import java.util.List;

public interface SystemSettingsProvider {

    SystemSettingsSnapshot snapshot();

    record SystemSettingsSnapshot(
            RuntimeSettings runtime,
            SmtpServerSettings smtpServer,
            DeliverySettings delivery,
            QuarantinePolicySettings quarantinePolicy,
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
            String keyAlias,
            String engine,
            String provider,
            String protocol,
            List<String> enabledProtocols,
            List<String> enabledCipherSuites
    ) {
        public TlsSettings {
            enabledProtocols = enabledProtocols == null ? List.of() : List.copyOf(enabledProtocols);
            enabledCipherSuites = enabledCipherSuites == null ? List.of() : List.copyOf(enabledCipherSuites);
        }
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
            SmtpTransportSecurity transportSecurity,
            int timeoutMs,
            String envelopeFrom
    ) {
    }

    record RelaySettings(
            String host,
            int port,
            boolean useTls,
            SmtpTransportSecurity transportSecurity,
            int timeoutMs,
            boolean usernameConfigured,
            boolean passwordConfigured,
            String username,
            String password
    ) {
        public RelaySettings redacted() {
            return new RelaySettings(host, port, useTls, transportSecurity, timeoutMs, usernameConfigured, passwordConfigured, null, null);
        }
    }

    record QuarantinePolicySettings(
            int maxRetentionDays,
            boolean notificationEnabled,
            boolean releaseRequiresEncryption
    ) {
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
