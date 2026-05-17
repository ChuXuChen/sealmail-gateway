package com.sealmail.app.dto.response;

import java.time.Instant;
import java.util.List;

public record SystemSettingsResponse(
        RuntimeResponse runtime,
        SmtpServerResponse smtpServer,
        DeliveryResponse delivery,
        GmEdgeResponse gmEdge,
        SmimeSuitePolicyResponse smimeSuitePolicy,
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
            int maxMessageSizeBytes
    ) {
    }

    public record DeliveryResponse(
            String mode,
            PostfixResponse postfix,
            RelayResponse directRelay
    ) {
    }

    public record GmEdgeResponse(
            boolean enabled,
            GmEdgeInboundResponse inbound,
            GmEdgeOutboundResponse outbound,
            GmEdgePostfixResponse postfix,
            GmEdgeTlsResponse tls,
            GmEdgeLimitsResponse limits,
            List<GmEdgeRouteResponse> routes
    ) {
        public GmEdgeResponse {
            routes = routes == null ? List.of() : List.copyOf(routes);
        }
    }

    public record GmEdgeInboundResponse(
            boolean enabled,
            String bindAddress,
            int startTlsPort,
            int implicitTlsPort,
            int backlog,
            int maxConnections
    ) {
    }

    public record GmEdgeOutboundResponse(
            boolean enabled,
            String bindAddress,
            int smartHostPort,
            int backlog,
            int maxConnections
    ) {
    }

    public record GmEdgePostfixResponse(
            String host,
            int port
    ) {
    }

    public record GmEdgeTlsResponse(
            List<String> protocols,
            List<String> cipherSuites,
            String keyStorePath,
            boolean keyStoreConfigured,
            boolean keyStorePasswordConfigured,
            String keyStorePasswordSecretRef,
            String keyStoreType,
            String trustStorePath,
            boolean trustStoreConfigured,
            boolean trustStorePasswordConfigured,
            String trustStorePasswordSecretRef,
            String trustStoreType,
            boolean trustAll
    ) {
        public GmEdgeTlsResponse {
            protocols = protocols == null ? List.of() : List.copyOf(protocols);
            cipherSuites = cipherSuites == null ? List.of() : List.copyOf(cipherSuites);
        }
    }

    public record GmEdgeLimitsResponse(
            int connectTimeoutMs,
            int readTimeoutMs,
            int maxMessageSizeBytes,
            int maxLineLengthBytes,
            int maxRecipients
    ) {
    }

    public record GmEdgeRouteResponse(
            String domainPattern,
            String targetHost,
            int targetPort,
            String security
    ) {
    }

    public record PostfixResponse(
            boolean enabled,
            String host,
            int afterFilterPort,
            int outboundPort,
            int timeoutMs,
            String envelopeFrom
    ) {
    }

    public record RelayResponse(
            String host,
            int port,
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

    public record SmimeSuitePolicyResponse(
            String defaultStandardSuite,
            String defaultGmSuite,
            List<SmimeSuiteOptionResponse> standardSuites,
            List<SmimeSuiteOptionResponse> gmSuites
    ) {
        public SmimeSuitePolicyResponse {
            standardSuites = standardSuites == null ? List.of() : List.copyOf(standardSuites);
            gmSuites = gmSuites == null ? List.of() : List.copyOf(gmSuites);
        }
    }

    public record SmimeSuiteOptionResponse(
            String id,
            String displayName,
            String profile
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
