package com.sealmail.domain.system;

import java.time.Instant;
import java.util.List;

public interface SystemSettingsProvider {

    SystemSettingsSnapshot snapshot();

    record SystemSettingsSnapshot(
            RuntimeSettings runtime,
            SmtpServerSettings smtpServer,
            DeliverySettings delivery,
            GmEdgeSettings gmEdge,
            SmimeSuitePolicySettings smimeSuitePolicy,
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
            int maxMessageSizeBytes
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
            int timeoutMs,
            String envelopeFrom
    ) {
    }

    record RelaySettings(
            String host,
            int port,
            int timeoutMs,
            boolean usernameConfigured,
            boolean passwordConfigured,
            String username,
            String password
    ) {
        public RelaySettings redacted() {
            return new RelaySettings(host, port, timeoutMs, usernameConfigured, passwordConfigured, null, null);
        }
    }

    record QuarantinePolicySettings(
            int maxRetentionDays,
            boolean notificationEnabled,
            boolean releaseRequiresEncryption
    ) {
    }

    record SmimeSuitePolicySettings(
            String defaultStandardSuite,
            String defaultGmSuite,
            List<SmimeSuiteOption> standardSuites,
            List<SmimeSuiteOption> gmSuites
    ) {
        public SmimeSuitePolicySettings {
            standardSuites = standardSuites == null ? List.of() : List.copyOf(standardSuites);
            gmSuites = gmSuites == null ? List.of() : List.copyOf(gmSuites);
        }
    }

    record SmimeSuiteOption(
            String id,
            String displayName,
            String profile
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
